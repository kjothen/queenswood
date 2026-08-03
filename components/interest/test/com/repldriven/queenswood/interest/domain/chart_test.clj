(ns com.repldriven.queenswood.interest.domain.chart-test
  "What each kind of run needs out of a bank's chart of accounts, and
  what it says when the chart cannot supply it."
  (:require
    [com.repldriven.queenswood.interest.domain.chart :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- ledger-account
  [gl-account-code id]
  {:gl-account-code gl-account-code :ledger-account-id id})

(def ^:private full-chart
  [(ledger-account :gl-account-code-interest-expense "led.expense")
   (ledger-account :gl-account-code-interest-payable "led.payable")
   (ledger-account :gl-account-code-customer-deposits-current "led.current")
   (ledger-account :gl-account-code-customer-deposits-savings "led.savings")
   (ledger-account :gl-account-code-customer-deposits-term "led.term")
   (ledger-account :gl-account-code-own-funds "led.own-funds")
   (ledger-account :gl-account-code-suspense "led.suspense")])

(defn- without
  [gl-account-code]
  (vec (remove (fn [a] (= gl-account-code (:gl-account-code a))) full-chart)))

(deftest accrual-accounts-test
  (testing "accrual takes the two fixed roles and ignores the rest"
    (is (= {:expense "led.expense" :payable "led.payable"}
           (SUT/accrual-accounts full-chart "org.1"))))
  (testing "a chart missing a role names it rather than posting blind"
    (let [result (SUT/accrual-accounts (without
                                        :gl-account-code-interest-expense)
                                       "org.1")]
      (is (error/rejection? result))
      (is (= :interest/missing-gl-account (error/kind result))))))

(deftest capitalization-accounts-test
  (testing "capitalisation takes payable plus a control per product type"
    ;; Every product type that rolls into a control, including own
    ;; funds — which pays no interest today but would land here the day
    ;; it does.
    (is (= {:payable "led.payable"
            :controls {:product-type-sub-ledger-current "led.current"
                       :product-type-sub-ledger-savings "led.savings"
                       :product-type-sub-ledger-term-deposit "led.term"
                       :product-type-sub-ledger-own-funds "led.own-funds"}}
           (SUT/capitalization-accounts full-chart "org.1"))))
  (testing "a missing deposit control is a rejection, not a nil credit leg"
    ;; Every earning product type must have somewhere for its
    ;; capitalised interest to land before any of it moves.
    (let [result (SUT/capitalization-accounts
                  (without :gl-account-code-customer-deposits-savings)
                  "org.1")]
      (is (error/rejection? result))
      (is (= :interest/missing-gl-account (error/kind result)))
      (is (= :gl-account-code-customer-deposits-savings
             (:gl-account-code (error/payload result))))))
  (testing "a missing payable is caught the same way"
    (is (error/rejection? (SUT/capitalization-accounts
                           (without :gl-account-code-interest-payable)
                           "org.1")))))
