(ns com.repldriven.mono.bank-cash-account.domain-test
  "Pure-function tests for close-account's guards. No FDB, no
  processor — this pins the lifecycle-transition convention
  (docs/recipes/lifecycle-transitions.md): reject before any
  capability/limit check when the account isn't in a closeable
  state, and pins the QNS-19 zero-balance-on-close invariant with
  its policy-based opt-out."
  (:require
    [com.repldriven.mono.bank-cash-account.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- account
  [status]
  {:account-id "acc.test"
   :account-type :account-type-personal
   :account-status status
   :currency "GBP"})

(defn- balance
  [credit debit]
  {:balance-type :balance-type-default
   :balance-status :balance-status-posted
   :currency "GBP"
   :credit credit
   :debit debit})

(def ^:private close-only-policies
  [{:enabled true
    :capabilities [{:kind {:cash-account {:action :cash-account-action-close}}
                    :effect :effect-allow}]}])

(def ^:private close-and-non-zero-policies
  [{:enabled true
    :capabilities [{:kind {:cash-account {:action :cash-account-action-close}}
                    :effect :effect-allow}
                   {:kind {:cash-account {:action
                                          :cash-account-action-close-non-zero}}
                    :effect :effect-allow}]}])

(deftest close-account-source-state-guard-test
  (testing
    "closing an account not in :cash-account-status-opened is
           rejected, regardless of policy"
    (doseq [status [:cash-account-status-opening
                    :cash-account-status-closing
                    :cash-account-status-closed]]
      (let [result (SUT/close-account (account status) [] [])]
        (is (error/rejection? result))
        (is (= :cash-account/invalid-status (error/kind result)))
        (is (= status (:status (error/payload result))))))))

(deftest close-account-zero-balance-guard-test
  (let [opened (account :cash-account-status-opened)]
    (testing "an all-zero balance closes without the opt-out capability"
      (let [result
            (SUT/close-account opened [(balance 0 0)] close-only-policies)]
        (is (= :cash-account-status-closing (:account-status result)))))
    (testing "a non-zero balance is rejected without the opt-out capability"
      (let [result
            (SUT/close-account opened [(balance 500 0)] close-only-policies)]
        (is (error/rejection? result))
        (is (= :cash-account/non-zero-on-close (error/kind result)))
        (is (= "acc.test" (:account-id (error/payload result))))
        (is (= {:value 500 :currency "GBP"}
               (:posted-balance (error/payload result))))))
    (testing "a non-zero balance closes with the opt-out capability granted"
      (let [result (SUT/close-account opened
                                      [(balance 500 0)]
                                      close-and-non-zero-policies)]
        (is (= :cash-account-status-closing (:account-status result)))))))
