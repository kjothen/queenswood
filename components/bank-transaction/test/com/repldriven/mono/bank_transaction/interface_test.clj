(ns ^:eftest/synchronized com.repldriven.mono.bank-transaction.interface-test
  (:require
    [com.repldriven.mono.bank-transaction.interface]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface :as organizations]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name)
                                data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc
                         {:command command-name
                          :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name)
                         (:payload result)))

(defn- seed-balance
  [config account-id]
  (balances/new-balance config
                        {:account-id account-id
                         :product-type :product-type-current
                         :balance-type :balance-type-default
                         :currency "GBP"
                         :balance-status :balance-status-posted}))

(defn- test-record-transaction
  [config proc schemas]
  (testing "record-transaction creates transaction with legs"
    (seed-balance config "acc_001")
    (seed-balance config "acc_002")
    (let [data
          {:idempotency-key "idem-001"
           :transaction-type :transaction-type-inbound-transfer
           :currency "GBP"
           :reference "Test transfer"
           :legs [{:account-id "acc_001"
                   :balance-type :balance-type-default
                   :balance-status :balance-status-posted
                   :side :leg-side-debit
                   :amount 1000}
                  {:account-id "acc_002"
                   :balance-type :balance-type-default
                   :balance-status :balance-status-posted
                   :side :leg-side-credit
                   :amount 1000}]}]
      (nom-test>
        [result (send-command proc
                              schemas
                              "record-transaction"
                              data)
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas
                                 "transaction"
                                 result)
         _ (is (some? (:transaction-id decoded)))
         _ (is (= :transaction-status-pending
                  (:status decoded)))
         _ (is (= :transaction-type-inbound-transfer
                  (:transaction-type decoded)))
         _ (is (= "GBP" (:currency decoded)))
         _ (is (= "Test transfer" (:reference decoded)))
         _ (is (= 2 (count (:legs decoded))))
         _ (is (every? #(some? (:leg-id %))
                       (:legs decoded)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result
          (send-command
           proc
           schemas
           "unknown-transaction-command"
           {:idempotency-key "idem-999"
            :transaction-type :transaction-type-fee
            :currency "GBP"
            :legs [{:account-id "acc_001"
                    :balance-type :balance-type-default
                    :balance-status :balance-status-posted
                    :side :leg-side-debit
                    :amount 100}]})]
      (is (error/rejection? result))
      (is (= :transaction/unknown-command
             (error/kind result))))))

(defn- test-simulate-inbound-transfer-customer-org
  ;; Posts an INTERNAL_TRANSFER from internal org's suspense/posted
  ;; to customer org's default/posted, simulating an inbound
  ;; bank transfer funding the customer org's account
  [sys fdb-config proc schemas]
  (testing "simulate inbound transfer funding customer org account"
    (let [tier-id (:tier-id (system/instance sys [:tiers :micro]))
          internal-org (system/instance sys
                                        [:organizations :internal])
          internal-account-id (get-in internal-org
                                      [:organization :accounts
                                       0 :account-id])]
      (nom-test>
        [customer-org (organizations/new-organization
                       fdb-config
                       "Test Customer"
                       :organization-type-customer
                       :organization-status-test
                       tier-id
                       ["GBP"])
         customer-account-id (get-in customer-org
                                     [:organization :accounts
                                      0 :account-id])
         result (send-command
                 proc
                 schemas
                 "record-transaction"
                 {:idempotency-key "idem-transfer-001"
                  :transaction-type
                  :transaction-type-internal-transfer
                  :currency "GBP"
                  :reference "Internal to customer"
                  :legs [{:account-id internal-account-id
                          :balance-type :balance-type-suspense
                          :balance-status :balance-status-posted
                          :side :leg-side-debit
                          :amount 100}
                         {:account-id customer-account-id
                          :balance-type :balance-type-default
                          :balance-status :balance-status-posted
                          :side :leg-side-credit
                          :amount 100}]})
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas "transaction" result)
         _ (is (= :transaction-status-posted
                  (:status decoded)))
         internal-suspense
         (balances/get-balance fdb-config
                               internal-account-id
                               :balance-type-suspense
                               "GBP"
                               :balance-status-posted)
         _ (is (= 0 (:credit internal-suspense)))
         _ (is (= 100 (:debit internal-suspense)))
         customer-default
         (balances/get-balance fdb-config
                               customer-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 100 (:credit customer-default)))
         _ (is (= 0 (:debit customer-default)))]))))

(deftest process-transaction-test
  (with-test-system
   [sys "classpath:bank-transaction/application-test.yml"]
   (let [proc (system/instance sys [:transactions :processor])
         schemas (system/instance sys [:avro :serde])
         config (fdb-config sys)]
     (test-record-transaction config proc schemas)
     (test-unknown-command proc schemas)
     (test-simulate-inbound-transfer-customer-org sys config proc schemas))))
