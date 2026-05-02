(ns ^:eftest/synchronized com.repldriven.mono.bank-cash-account.interface-test
  (:require
    [com.repldriven.mono.bank-cash-account.commands :as commands]
    [com.repldriven.mono.bank-cash-account.interface]

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

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result (#'commands/dispatch
                  {:schemas {}}
                  {:command "unknown-cash-account-command" :payload nil})]
      (is (error/rejection? result))
      (is (= :cash-account/unknown-command (error/kind result))))))

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

(deftest process-cash-accounts-test
  (with-test-system
   [sys "classpath:bank-cash-account/application-test.yml"]
   (let [proc (system/instance sys [:cash-account :processor])
         schemas (system/instance sys [:avro :serde])
         config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}]
     ;; Subtests that open an account share a single
     ;; published product seeded once here; tests that
     ;; need a different product (no-payment-schemes,
     ;; invalid-currency) seed their own. The shared
     ;; org and a "cust-1" active party are similarly
     ;; pre-seeded for tests that don't seed their own.
     (seed-organization config)
     (seed-active-party config "cust-1")
     (seed-published-product-version config test-product-id)
     (test-open-account-party-not-found proc schemas)
     (test-watcher-transitions proc schemas config)
     (test-close-missing-account proc schemas)
     (test-open-account-unknown-product proc schemas)
     (test-open-account-no-payment-schemes proc schemas config))))
