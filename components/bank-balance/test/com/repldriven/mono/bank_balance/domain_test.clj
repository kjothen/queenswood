(ns com.repldriven.mono.bank-balance.domain-test
  (:require
    [com.repldriven.mono.bank-balance.domain :as SUT]

    [clojure.test :refer [deftest is testing]]))

(defn- balance
  [balance-type balance-status credit debit]
  {:balance-type balance-type
   :balance-status balance-status
   :credit credit
   :debit debit})

(def ^:private customer-balances
  [(balance :balance-type-default :balance-status-posted 10000 2000)
   (balance :balance-type-default :balance-status-pending-incoming 500 0)
   (balance :balance-type-default :balance-status-pending-outgoing 0 300)])

(def ^:private gl-balances
  "Bank-side GL account — single default/posted bucket; nothing
  customer-facing about it."
  [(balance :balance-type-default :balance-status-posted 50000 10000)])

(deftest posted-balance-test
  (testing "returns net of default/posted"
    (is (= {:value 8000 :currency "GBP"}
           (SUT/posted-balance customer-balances "GBP"))))
  (testing "returns zero when no default/posted"
    (is (= {:value 0 :currency "GBP"} (SUT/posted-balance [] "GBP")))))

(deftest available-balance-current-test
  (testing "current = posted + pending-incoming + pending-outgoing"
    ;; 8000 + 500 + (-300) = 8200
    (is (= {:value 8200 :currency "GBP"}
           (SUT/available-balance :product-type-sub-ledger-current
                                  customer-balances
                                  "GBP")))))

(deftest available-balance-savings-test
  (testing "savings same formula as current"
    (is (= {:value 8200 :currency "GBP"}
           (SUT/available-balance :product-type-sub-ledger-savings
                                  customer-balances
                                  "GBP")))))

(deftest available-balance-term-deposit-test
  (testing "term-deposit same formula as current"
    (is (= {:value 8200 :currency "GBP"}
           (SUT/available-balance :product-type-sub-ledger-term-deposit
                                  customer-balances
                                  "GBP")))))

(deftest available-balance-gl-account-test
  (testing "GL accounts (nil :product-type) fall back to default-posted net"
    ;; 40000 net default-posted; no "available" concept for GL accounts.
    (is (= {:value 40000 :currency "GBP"}
           (SUT/available-balance nil gl-balances "GBP")))))

(deftest available-balance-unknown-type-test
  (testing "unknown type falls back to posted"
    (is (= {:value 8000 :currency "GBP"}
           (SUT/available-balance :product-type-unknown
                                  customer-balances
                                  "GBP")))))

(deftest available-balance-empty-test
  (testing "empty balances returns zero"
    (is (= {:value 0 :currency "GBP"}
           (SUT/available-balance :product-type-sub-ledger-current [] "GBP")))))
