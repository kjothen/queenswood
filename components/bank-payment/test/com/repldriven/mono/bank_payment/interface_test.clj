(ns ^:eftest/synchronized com.repldriven.mono.bank-payment.interface-test
  (:require
    [com.repldriven.mono.bank-payment.interface]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface :as
     organizations]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

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

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- test-submit-internal-payment
  [sys proc schemas]
  (testing "submit-internal-payment creates payment and
  records transaction"
    (let [config (fdb-config sys)
          internal-org (system/instance sys
                                        [:organizations :internal])
          internal-account-id (get-in internal-org
                                      [:organization :accounts
                                       0 :account-id])]
      (nom-test>
        [customer-org (organizations/new-organization
                       config
                       "Payment Test Customer"
                       :organization-type-customer
                       :tier-type-micro
                       ["GBP"])
         customer-account-id
         (get-in customer-org
                 [:organization :accounts 0 :account-id])
         result (send-command
                 proc
                 schemas
                 "submit-internal-payment"
                 {:idempotency-key "pmt-idem-001"
                  :debtor-account-id internal-account-id
                  :creditor-account-id customer-account-id
                  :currency "GBP"
                  :amount 500
                  :reference "Test internal payment"})
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas
                                 "internal-payment"
                                 result)
         _ (is (some? (:payment-id decoded)))
         _ (is (= "pmt-idem-001" (:idempotency-key decoded)))
         _ (is (= internal-account-id
                  (:debtor-account-id decoded)))
         _ (is (= customer-account-id
                  (:creditor-account-id decoded)))
         _ (is (= "GBP" (:currency decoded)))
         _ (is (= 500 (:amount decoded)))
         _ (is (some? (:transaction-id decoded)))
         _ (is (= "Test internal payment"
                  (:reference decoded)))
         debtor-balance
         (balances/get-balance config
                               internal-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 500 (:debit debtor-balance)))
         creditor-balance
         (balances/get-balance config
                               customer-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 500 (:credit creditor-balance)))]))))

(defn- send-event
  [proc schemas event-name data]
  (let [payload (avro/serialize (get schemas event-name)
                                data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc
                         {:event event-name
                          :payload payload}))))

(defn- test-settle-inbound-payment
  [sys event-proc schemas]
  (testing
    "transaction-settled event creates inbound
  payment and records transaction"
    (let [config (fdb-config sys)
          internal-org (system/instance sys
                                        [:organizations :internal])
          internal-account-id (get-in internal-org
                                      [:organization :accounts
                                       0 :account-id])]
      (nom-test>
        [customer-org (organizations/new-organization
                       config
                       "Inbound Payment Customer"
                       :organization-type-customer
                       :tier-type-micro
                       ["GBP"])
         customer-account
         (get-in customer-org [:organization :accounts 0])
         customer-account-id (:account-id customer-account)
         bban (:bban customer-account)
         _ (is (some? bban))
         result (send-event
                 event-proc
                 schemas
                 "transaction-settled"
                 {:scheme-transaction-id "stx-001"
                  :end-to-end-id "e2e-001"
                  :scheme "FPS"
                  :debit-credit-code :debit-credit-code-credit
                  :amount 1000
                  :currency "GBP"
                  :creditor-bban bban
                  :debtor-name "Jane Doe"
                  :reference "Invoice 42"
                  :timestamp-settled (System/currentTimeMillis)})
         _ (is (some? (:payment-id result)))
         _ (is (= "stx-001"
                  (:scheme-transaction-id result)))
         _ (is (= "e2e-001" (:end-to-end-id result)))
         _ (is (= "FPS" (:scheme result)))
         _ (is (= customer-account-id
                  (:creditor-account-id result)))
         _ (is (= "GBP" (:currency result)))
         _ (is (= 1000 (:amount result)))
         _ (is (some? (:transaction-id result)))
         _ (is (= "Jane Doe" (:debtor-name result)))
         _ (is (= "Invoice 42" (:reference result)))
         creditor-balance
         (balances/get-balance config
                               customer-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 1000 (:credit creditor-balance)))
         internal-balance
         (balances/get-balance config
                               internal-account-id
                               :balance-type-suspense
                               "GBP"
                               :balance-status-posted)
         _ (is (= 1000 (:debit internal-balance)))
         ;; idempotency: re-send same event
         result2 (send-event
                  event-proc
                  schemas
                  "transaction-settled"
                  {:scheme-transaction-id "stx-001"
                   :end-to-end-id "e2e-001"
                   :scheme "FPS"
                   :debit-credit-code :debit-credit-code-credit
                   :amount 1000
                   :currency "GBP"
                   :creditor-bban bban
                   :debtor-name "Jane Doe"
                   :reference "Invoice 42"
                   :timestamp-settled (System/currentTimeMillis)})
         _ (is (= (:payment-id result)
                  (:payment-id result2)))]))))

(defn- test-submit-outbound-payment
  [sys proc schemas]
  (testing
    "submit-outbound-payment creates pending payment,
  debits debtor and credits suspense"
    (let [config (fdb-config sys)
          internal-org (system/instance sys
                                        [:organizations :internal])
          internal-account-id (get-in internal-org
                                      [:organization :accounts
                                       0 :account-id])]
      (nom-test>
        [customer-org (organizations/new-organization
                       config
                       "Outbound Payment Customer"
                       :organization-type-customer
                       :tier-type-micro
                       ["GBP"])
         customer-organization-id
         (get-in customer-org
                 [:organization :organization-id])
         customer-account-id
         (get-in customer-org
                 [:organization :accounts 0 :account-id])
         result (send-command
                 proc
                 schemas
                 "submit-outbound-payment"
                 {:idempotency-key "pmt-ob-idem-001"
                  :organization-id customer-organization-id
                  :debtor-account-id customer-account-id
                  :scheme "FPS"
                  :creditor-bban "87654321"
                  :creditor-name "External Recipient"
                  :currency "GBP"
                  :amount 500
                  :reference "Outbound test"})
         _ (is (= "ACCEPTED" (:status result)))
         decoded (decode-payload schemas
                                 "outbound-payment"
                                 result)
         _ (is (some? (:payment-id decoded)))
         _ (is (= "pmt-ob-idem-001"
                  (:idempotency-key decoded)))
         _ (is (= customer-account-id
                  (:debtor-account-id decoded)))
         _ (is (= "87654321" (:creditor-bban decoded)))
         _ (is (= "External Recipient"
                  (:creditor-name decoded)))
         _ (is (= "GBP" (:currency decoded)))
         _ (is (= 500 (:amount decoded)))
         _ (is (some? (:transaction-id decoded)))
         _ (is (= "Outbound test" (:reference decoded)))
         _ (is (= :outbound-payment-status-pending
                  (:payment-status decoded)))
         debtor-balance
         (balances/get-balance config
                               customer-account-id
                               :balance-type-default
                               "GBP"
                               :balance-status-posted)
         _ (is (= 500 (:debit debtor-balance)))
         suspense-balance
         (balances/get-balance config
                               internal-account-id
                               :balance-type-suspense
                               "GBP"
                               :balance-status-posted)
         _ (is (= 500 (:credit suspense-balance)))]))))

(defn- test-settle-outbound-payment
  [sys proc event-proc schemas]
  (testing
    "transaction-settled event completes the outbound
  payment settlement"
    (let [config (fdb-config sys)]
      (nom-test>
        [customer-org (organizations/new-organization
                       config
                       "Outbound Settlement Customer"
                       :organization-type-customer
                       :tier-type-micro
                       ["GBP"])
         customer-organization-id
         (get-in customer-org
                 [:organization :organization-id])
         customer-account-id
         (get-in customer-org
                 [:organization :accounts 0 :account-id])
         submit (send-command
                 proc
                 schemas
                 "submit-outbound-payment"
                 {:idempotency-key "pmt-ob-idem-100"
                  :organization-id customer-organization-id
                  :debtor-account-id customer-account-id
                  :scheme "FPS"
                  :creditor-bban "87654321"
                  :creditor-name "External Recipient"
                  :currency "GBP"
                  :amount 250
                  :reference "Outbound settlement test"})
         _ (is (= "ACCEPTED" (:status submit)))
         pending (decode-payload schemas
                                 "outbound-payment"
                                 submit)
         pending-payment-id (:payment-id pending)
         _ (is (some? pending-payment-id))
         _ (is (= :outbound-payment-status-pending
                  (:payment-status pending)))
         result (send-event
                 event-proc
                 schemas
                 "transaction-settled"
                 {:scheme-transaction-id "stx-ob-100"
                  :end-to-end-id pending-payment-id
                  :scheme "FPS"
                  :debit-credit-code :debit-credit-code-debit
                  :amount 250
                  :currency "GBP"
                  :creditor-bban "87654321"
                  :debtor-name "Outbound Settlement Customer"
                  :reference "Outbound settlement test"
                  :timestamp-settled (System/currentTimeMillis)})
         _ (is (= pending-payment-id (:payment-id result)))
         _ (is (= :outbound-payment-status-completed
                  (:payment-status result)))
         ;; idempotency: re-send same event
         result2 (send-event
                  event-proc
                  schemas
                  "transaction-settled"
                  {:scheme-transaction-id "stx-ob-100"
                   :end-to-end-id pending-payment-id
                   :scheme "FPS"
                   :debit-credit-code :debit-credit-code-debit
                   :amount 250
                   :currency "GBP"
                   :creditor-bban "87654321"
                   :debtor-name "Outbound Settlement Customer"
                   :reference "Outbound settlement test"
                   :timestamp-settled (System/currentTimeMillis)})
         _ (is (= pending-payment-id (:payment-id result2)))
         _ (is (= :outbound-payment-status-completed
                  (:payment-status result2)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result
          (send-command
           proc
           schemas
           "unknown-payment-command"
           {:idempotency-key "pmt-idem-999"
            :debtor-account-id "acc-1"
            :creditor-account-id "acc-2"
            :currency "GBP"
            :amount 100})]
      (is (error/rejection? result))
      (is (= :payment/unknown-command
             (error/kind result))))))

(deftest process-payment-test
  (with-test-system
   [sys "classpath:bank-payment/application-test.yml"]
   (let [proc (system/instance sys [:payment :processor])
         event-proc (system/instance sys [:payment :event-processor])
         schemas (system/instance sys [:avro :serde])]
     (test-submit-internal-payment sys proc schemas)
     (test-submit-outbound-payment sys proc schemas)
     (test-unknown-command proc schemas)
     (test-settle-inbound-payment sys event-proc schemas)
     (test-settle-outbound-payment sys proc event-proc schemas))))
