(ns ^:eftest/synchronized com.repldriven.queenswood.payment.store-test
  (:require
    [com.repldriven.queenswood.payment.store :as store]

    [com.repldriven.queenswood.payment-query.interface :as q]
    [com.repldriven.queenswood.fdb.interface]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.queenswood.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

(defn- internal-payment
  [payment-id idempotency-key]
  {:payment-id payment-id
   :idempotency-key idempotency-key
   :debtor-account-id "acc.debtor"
   :creditor-account-id "acc.creditor"
   :currency "GBP"
   :amount 1500
   :transaction-id "txn.internal"
   :created-at (utility/now)
   :updated-at (utility/now)
   :bank-id "bnk.test"
   :business-day 20260101})

(defn- outbound-payment
  [payment-id idempotency-key]
  {:payment-id payment-id
   :idempotency-key idempotency-key
   :scheme "fps"
   :debtor-account-id "acc.debtor"
   :creditor-bban "12345678901234"
   :creditor-name "Acme Ltd"
   :currency "GBP"
   :amount 2500
   :payment-status :outbound-payment-status-pending
   :transaction-id "txn.outbound"
   :created-at (utility/now)
   :bank-id "bnk.test"
   :business-day 20260101})

(deftest internal-payment-idempotency-read-back-test
  (with-test-system
   [sys "classpath:payment/application-test.yml"]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         key "idem-internal-000000000001"]
     (testing "first internal payment saves"
       (nom-test> [_ (store/save-internal-payment config
                                                  (internal-payment "pmt.i1"
                                                                    key))]))
     (testing "a second payment reusing the idempotency-key violates the index"
       (is (store/uniqueness-violation? (store/save-internal-payment
                                         config
                                         (internal-payment "pmt.i2" key)))))
     (testing "read-back returns the original payment, not the duplicate"
       (nom-test> [found (q/find-internal-payment-by-idempotency-key config key)
                   _ (is (= "pmt.i1" (:payment-id found)))
                   _ (is (= key (:idempotency-key found)))])))))

(deftest outbound-payment-idempotency-read-back-test
  (with-test-system
   [sys "classpath:payment/application-test.yml"]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         key "idem-outbound-000000000001"]
     (testing "first outbound payment saves"
       (nom-test> [_ (store/save-outbound-payment config
                                                  (outbound-payment "pmt.o1"
                                                                    key))]))
     (testing "a second payment reusing the idempotency-key violates the index"
       (is (store/uniqueness-violation? (store/save-outbound-payment
                                         config
                                         (outbound-payment "pmt.o2" key)))))
     (testing "read-back returns the original payment, not the duplicate"
       (nom-test> [found (q/find-outbound-payment-by-idempotency-key config key)
                   _ (is (= "pmt.o1" (:payment-id found)))
                   _ (is (= key (:idempotency-key found)))])))))
