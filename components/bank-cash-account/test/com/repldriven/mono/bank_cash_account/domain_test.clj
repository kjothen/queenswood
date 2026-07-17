(ns com.repldriven.mono.bank-cash-account.domain-test
  "Pure-function tests for the close-account source-state guard. No
  FDB, no processor — this pins the lifecycle-transition convention
  (docs/recipes/lifecycle-transitions.md): reject before any
  capability/limit check when the account isn't in a closeable
  state."
  (:require
    [com.repldriven.mono.bank-cash-account.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- account
  [status]
  {:account-id "acc.test"
   :account-type :account-type-personal
   :account-status status})

(deftest close-account-source-state-guard-test
  (testing
    "closing an account not in :cash-account-status-opened is
           rejected, regardless of policy"
    (doseq [status [:cash-account-status-opening
                    :cash-account-status-closing
                    :cash-account-status-closed]]
      (let [result (SUT/close-account (account status) [])]
        (is (error/rejection? result))
        (is (= :cash-account/invalid-status (error/kind result)))
        (is (= status (:status (error/payload result))))))))
