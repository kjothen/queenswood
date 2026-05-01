(ns ^:eftest/synchronized com.repldriven.mono.bank-cash-account.interface-test
  (:require
    [com.repldriven.mono.bank-cash-account.interface]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(def ^:private test-org-id "org_test_cash_accounts")
(def ^:private test-product-id "prd_test_cash_accounts")

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc {:command command-name :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(defn- poll-status
  "Polls get-cash-account until account-status matches
  expected, or times out after 5 s."
  [proc schemas account-id expected]
  (loop [attempts 50]
    (let [result (send-command proc
                               schemas
                               "get-cash-account"
                               {:organization-id test-org-id
                                :account-id account-id})
          decoded (when (= "ACCEPTED" (:status result))
                    (decode-payload schemas "cash-account" result))]
      (cond (= expected (:account-status decoded))
            decoded
            (pos? attempts)
            (do (Thread/sleep 100) (recur (dec attempts)))
            :else
            decoded))))

(defn- seed-organization
  "Seeds an organization record in the organizations store."
  [config]
  (fdb/transact config
                (fn [txn]
                  (let [now (System/currentTimeMillis)
                        org {:organization-id test-org-id
                             :name "Test Org"
                             :type :organization-type-customer
                             :status :organization-status-test
                             :created-at now
                             :updated-at now}]
                    (fdb/save-record (fdb/open txn "organizations")
                                     (schema/Organization->java org))))))

(defn- seed-active-party
  "Seeds an active party record in the parties store."
  [config party-id]
  (fdb/transact config
                (fn [txn]
                  (let [party {:organization-id test-org-id
                               :party-id party-id
                               :type :party-type-person
                               :status :party-status-active
                               :display-name "Test Party"
                               :created-at (System/currentTimeMillis)
                               :updated-at (System/currentTimeMillis)}]
                    (fdb/save-record (fdb/open txn "parties")
                                     (schema/Party->java party))))))

(def ^:private default-balance-products
  [{:balance-type :balance-type-default :balance-status :balance-status-posted}
   {:balance-type :balance-type-default
    :balance-status :balance-status-pending-incoming}
   {:balance-type :balance-type-default
    :balance-status :balance-status-pending-outgoing}])

(def ^:private default-payment-address-schemes [:payment-address-scheme-scan])

(defn- seed-published-product-version
  "Seeds a published CashAccountProduct in the
  cash-account-products store."
  ([config product-id]
   (seed-published-product-version config product-id {}))
  ([config product-id overrides]
   (fdb/transact
    config
    (fn [txn]
      (let [version
            (merge {:organization-id test-org-id
                    :product-id product-id
                    :version-id "prv_test_001"
                    :version-number 1
                    :status :cash-account-product-status-published
                    :product-type :product-type-current
                    :balance-sheet-side
                    :balance-sheet-side-liability
                    :name "Test Product"
                    :allowed-currencies []
                    :balance-products default-balance-products
                    :allowed-payment-address-schemes
                    default-payment-address-schemes
                    :created-at (System/currentTimeMillis)
                    :updated-at (System/currentTimeMillis)}
                   overrides)]
        (fdb/save-record
         (fdb/open txn "cash-account-products")
         (schema/CashAccountProduct->java version)))))))

(defn- seed-party
  "Seeds a party record with given status."
  [config party-id status]
  (fdb/transact config
                (fn [txn]
                  (let [party {:organization-id test-org-id
                               :party-id party-id
                               :type :party-type-person
                               :status status
                               :display-name "Test Party"
                               :created-at (System/currentTimeMillis)
                               :updated-at (System/currentTimeMillis)}]
                    (fdb/save-record (fdb/open txn "parties")
                                     (schema/Party->java party))))))

(defn- test-open-account
  [proc schemas config]
  (testing
    "open-cash-account creates account with opened status,
  payment addresses, and balances from product"
    (let [party-id "cust-1"
          open-payload {:organization-id test-org-id
                        :party-id party-id
                        :name "Test Account"
                        :currency "USD"
                        :product-id test-product-id}]
      (seed-organization config)
      (seed-active-party config party-id)
      (seed-published-product-version config test-product-id)
      (nom-test>
        [result (send-command proc schemas "open-cash-account" open-payload)
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas "cash-account" result)
         _ (is (some? (:account-id decoded)))
         _ (is (= :cash-account-status-opening (:account-status decoded)))
         _ (is (= test-product-id (:product-id decoded)))
         _ (is (string? (:version-id decoded)))
         _ (is (= open-payload (select-keys decoded (keys open-payload))))
         _ (is (= "040004"
                  (get-in decoded [:payment-addresses 0 :scan :sort-code])))
         _ (is (some? (get-in decoded
                              [:payment-addresses 0 :scan :account-number])))
         balance-result (balances/get-balances config (:account-id decoded))
         account-balances (:balances balance-result)
         _ (is (= 3 (count account-balances)))
         _ (is (= #{:balance-type-default}
                  (set (map :balance-type account-balances))))
         _ (is (= #{:balance-status-posted :balance-status-pending-incoming
                    :balance-status-pending-outgoing}
                  (set (map :balance-status account-balances))))
         _ (is (every? #(= "USD" (:currency %)) account-balances))
         _ (is (every? #(= 0 (:credit %)) account-balances))]))))

(defn- test-open-account-party-not-active
  [proc schemas config]
  (testing "open-cash-account rejects when party is not active"
    (let [party-id "cust-pending"]
      (seed-organization config)
      (seed-party config party-id :party-status-pending)
      (let [result (send-command proc
                                 schemas
                                 "open-cash-account"
                                 {:organization-id test-org-id
                                  :party-id party-id
                                  :name "Pending Account"
                                  :currency "USD"
                                  :product-id test-product-id})]
        (is (error/rejection? result))
        (is (= :cash-account/party-status (error/kind result)))))))

(defn- test-open-account-party-not-found
  [proc schemas]
  (testing "open-cash-account rejects when party not found"
    (let [result (send-command proc
                               schemas
                               "open-cash-account"
                               {:organization-id test-org-id
                                :party-id "nonexistent"
                                :name "Ghost Account"
                                :currency "USD"
                                :product-id test-product-id})]
      (is (error/rejection? result))
      (is (= :party/not-found (error/kind result))))))

(defn- test-close-account
  [proc schemas config]
  (testing "close-cash-account sets status to closing"
    (let [party-id "cust-2"
          open-payload {:organization-id test-org-id
                        :party-id party-id
                        :name "Account to Close"
                        :currency "USD"
                        :product-id test-product-id}]
      (seed-organization config)
      (seed-active-party config party-id)
      (nom-test>
        [opened (send-command proc schemas "open-cash-account" open-payload)
         account (decode-payload schemas "cash-account" opened)
         close-data (select-keys account [:organization-id :account-id])
         result (send-command proc schemas "close-cash-account" close-data)
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas "cash-account" result)
         _ (is (= open-payload (select-keys decoded (keys open-payload))))]))))

(defn- test-watcher-transitions
  [proc schemas config]
  (testing "watcher transitions opening->opened and closing->closed"
    (let [party-id "cust-watcher"
          open-payload {:organization-id test-org-id
                        :party-id party-id
                        :name "Watcher Account"
                        :currency "GBP"
                        :product-id test-product-id}]
      (seed-organization config)
      (seed-active-party config party-id)
      (nom-test>
        [opening (send-command proc schemas "open-cash-account" open-payload)
         account (decode-payload schemas "cash-account" opening)
         account-id (:account-id account)
         _ (is (= :cash-account-status-opening (:account-status account)))
         polled-opened
         (poll-status proc schemas account-id :cash-account-status-opened)
         _ (is (= :cash-account-status-opened (:account-status polled-opened)))
         closing (send-command proc
                               schemas
                               "close-cash-account"
                               {:organization-id test-org-id
                                :account-id account-id})
         closing-account (decode-payload schemas "cash-account" closing)
         _ (is (= :cash-account-status-closing
                  (:account-status closing-account)))
         polled-closed
         (poll-status proc schemas account-id :cash-account-status-closed)
         _ (is (= :cash-account-status-closed (:account-status polled-closed)))]))))

(defn- test-get-account
  [proc schemas config]
  (testing "get-cash-account returns account"
    (let [party-id "cust-7"
          open-payload {:organization-id test-org-id
                        :party-id party-id
                        :name "Status Account"
                        :currency "USD"
                        :product-id test-product-id}]
      (seed-organization config)
      (seed-active-party config party-id)
      (nom-test> [opened
                  (send-command proc schemas "open-cash-account" open-payload)
                  account (decode-payload schemas "cash-account" opened)
                  get-data (select-keys account [:organization-id :account-id])
                  result (send-command proc schemas "get-cash-account" get-data)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "cash-account" result)
                  _ (is (= (:account-id account) (:account-id decoded)))
                  _ (is (= :cash-account-status-opening
                           (:account-status decoded)))]))))

(defn- test-close-missing-account
  [proc schemas]
  (testing "close-cash-account rejects missing account"
    (let [result (send-command proc
                               schemas
                               "close-cash-account"
                               {:organization-id test-org-id
                                :account-id "missing-id"})]
      (is (error/rejection? result))
      (is (= :cash-account/not-found (error/kind result))))))

;; test-open-multiple-accounts-per-party removed — only asserted
;; that two openings :ACCEPTED. The scenario runner's full-happy-
;; path opens four customer accounts per org across two parties,
;; which exercises the same path. The test name implied
;; per-party-count partitioning but never asserted it.

;; test-open-accounts-independent-per-party removed — the test
;; name implied count-partitioning across parties but the body only
;; asserted that openings :ACCEPTED and party-ids round-tripped.
;; full-happy-path covers the same shape (two parties, multiple
;; accounts each).
#_(defn- test-open-accounts-independent-per-party
    [proc schemas config]
    (testing
      "account counts are tracked independently per
  party \u2014 one party's accounts don't count against another's"
      (let [party-a "cust-independent-a"
            party-b "cust-independent-b"
            payload {:organization-id test-org-id
                     :currency "USD"
                     :product-id test-product-id}]
        (seed-organization config)
        (seed-active-party config party-a)
        (seed-active-party config party-b)
        (nom-test> [a1 (send-command proc
                                     schemas
                                     "open-cash-account"
                                     (assoc payload
                                            :party-id party-a
                                            :name "A1"))
                    _ (is (= "ACCEPTED" (:status a1)))
                    b1 (send-command proc
                                     schemas
                                     "open-cash-account"
                                     (assoc payload
                                            :party-id party-b
                                            :name "B1"))
                    _ (is (= "ACCEPTED" (:status b1)))
                    b2 (send-command proc
                                     schemas
                                     "open-cash-account"
                                     (assoc payload
                                            :party-id party-b
                                            :name "B2"))
                    _ (is (= "ACCEPTED" (:status b2)))
                    da1 (decode-payload schemas "cash-account" a1)
                    db1 (decode-payload schemas "cash-account" b1)
                    db2 (decode-payload schemas "cash-account" b2)
                    _ (is (= party-a (:party-id da1)))
                    _ (is (= party-b (:party-id db1)))
                    _ (is (= party-b (:party-id db2)))]))))

(defn- test-open-account-unknown-product
  [proc schemas]
  (testing "open-cash-account rejects when the product-id
  has no versions"
    (let [result (send-command proc
                               schemas
                               "open-cash-account"
                               {:organization-id test-org-id
                                :party-id "cust-1"
                                :name "No Version"
                                :currency "USD"
                                :product-id "prd_no_versions"})]
      (is (error/rejection? result))
      (is (= :cash-account-product/product-not-found
             (error/kind result))))))

(defn- test-open-account-invalid-currency
  [proc schemas config]
  (testing "open-cash-account rejects when currency not in
  allowed-currencies"
    (let [party-id "cust-currency"
          product-id "prd_gbp_only"]
      (seed-organization config)
      (seed-active-party config party-id)
      (seed-published-product-version config
                                      product-id
                                      {:allowed-currencies ["GBP"]})
      (let [result (send-command proc
                                 schemas
                                 "open-cash-account"
                                 {:organization-id test-org-id
                                  :party-id party-id
                                  :name "Wrong Currency"
                                  :currency "USD"
                                  :product-id product-id})]
        (is (error/rejection? result))
        (is (= :cash-account/invalid-currency (error/kind result)))))))

(defn- test-open-account-no-payment-schemes
  [proc schemas config]
  (testing
    "open-cash-account rejects when product has no
  payment address schemes"
    (let [party-id "cust-no-schemes"
          product-id "prd_no_schemes"]
      (seed-organization config)
      (seed-active-party config party-id)
      (seed-published-product-version
       config
       product-id
       {:allowed-payment-address-schemes []})
      (let [result
            (send-command proc
                          schemas
                          "open-cash-account"
                          {:organization-id test-org-id
                           :party-id party-id
                           :name "No Schemes"
                           :currency "USD"
                           :product-id product-id})]
        (is (error/rejection? result))
        (is (= :cash-account/no-payment-schemes
               (error/kind result)))))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result (send-command proc
                               schemas
                               "unknown-command"
                               {:organization-id test-org-id
                                :account-id "acc-8"})]
      (is (error/rejection? result))
      (is (= :cash-account/unknown-command (error/kind result))))))

(deftest process-cash-accounts-test
  (with-test-system [sys "classpath:bank-cash-account/application-test.yml"]
                    (let [proc (system/instance sys [:cash-account :processor])
                          schemas (system/instance sys [:avro :serde])
                          config
                          {:record-db (system/instance sys [:fdb :record-db])
                           :record-store (system/instance sys [:fdb :store])}]
                      (test-open-account proc schemas config)
                      (test-open-account-party-not-active proc schemas config)
                      (test-open-account-party-not-found proc schemas)
                      (test-close-account proc schemas config)
                      (test-watcher-transitions proc schemas config)
                      (test-get-account proc schemas config)
                      (test-close-missing-account proc schemas)
                      (test-open-account-unknown-product proc schemas)
                      (test-open-account-invalid-currency proc schemas config)
                      (test-open-account-no-payment-schemes proc schemas config)
                      (test-unknown-command proc schemas))))
