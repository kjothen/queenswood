(ns ^:eftest/synchronized
    com.repldriven.mono.bank-test-projections.interface-test
  (:require
    [com.repldriven.mono.bank-test-projections.interface :as SUT]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.bank-test-model.interface :as model]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(defn- fund-customer
  "Posts an internal-transfer that debits the internal org's
  suspense and credits the customer's default — same shape the
  bank-interest tests use to drive accounts above zero. Internal's
  available is default-only, so debiting suspense leaves it
  unchanged and the platform `available >= 0` rule stays happy."
  [config internal-account-id customer-account-id amount idempotency-key]
  (fdb/transact
   config
   (fn [txn]
     (let [result (transactions/record-transaction
                   txn
                   {:idempotency-key idempotency-key
                    :transaction-type :transaction-type-internal-transfer
                    :currency "GBP"
                    :reference "Test fund"
                    :legs [{:account-id internal-account-id
                            :balance-type :balance-type-suspense
                            :balance-status :balance-status-posted
                            :side :leg-side-debit
                            :amount amount}
                           {:account-id customer-account-id
                            :balance-type :balance-type-default
                            :balance-status :balance-status-posted
                            :side :leg-side-credit
                            :amount amount}]})]
       (balances/apply-legs txn
                            (:legs result)
                            (:transaction-type result))))))

(deftest project-model-balances-test
  (testing "reads :available off each model account"
    (let [state (-> model/init-state
                    (assoc-in [:accounts :acct-0] {:available 100})
                    (assoc-in [:accounts :acct-1] {:available -50}))]
      (is (= {:acct-0 100 :acct-1 -50} (SUT/project-model-balances state)))))
  (testing "empty state projects to empty map"
    (is (= {} (SUT/project-model-balances model/init-state)))))

(deftest project-balances-test
  (with-test-system
   [sys "classpath:bank-test-projections/application-test.yml"]
   (let [config (fdb-config sys)
         internal-org (system/instance sys [:organizations :internal])
         internal-account-id (get-in internal-org
                                     [:organization :accounts
                                      0 :account-id])]
     (nom-test> [customer (organizations/new-organization
                           config
                           "Projection Test"
                           :organization-type-customer :organization-status-test
                           "micro" ["GBP"])
                 customer-account-id
                 (get-in customer [:organization :accounts 0 :account-id])
                 id-mapping {customer-account-id :acct-0}
                 _ (testing "fresh account projects as zero"
                     (is (= {:acct-0 0}
                            (SUT/project-balances config id-mapping))))
                 _ (fund-customer config
                                  internal-account-id
                                  customer-account-id
                                  500
                                  "projection-test-fund-001")
                 _ (testing "funded account projects as the credited amount"
                     (is (= {:acct-0 500}
                            (SUT/project-balances config id-mapping))))
                 _ (testing "empty id-mapping projects to empty map"
                     (is (= {} (SUT/project-balances config {}))))]))))
