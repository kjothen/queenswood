(ns com.repldriven.queenswood.cash-account.domain-test
  "Pure-function tests for the close/suspend/reopen/rotate-address
  source-state guards. No FDB, no processor — this pins the
  lifecycle-transition convention (docs/recipes/lifecycle-transitions.md):
  reject before any capability/limit check when the account isn't in
  a valid source state."
  (:require
    [com.repldriven.queenswood.cash-account.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- account
  [status]
  {:account-id "acc.test"
   :account-type :account-type-personal
   :account-status status
   :currency "GBP"})

(defn- policy-allowing
  [& actions]
  {:enabled true
   :capabilities (mapv (fn [action]
                         {:effect :effect-allow
                          :kind {:cash-account {:action action}}})
                       actions)})

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

(deftest close-account-non-zero-balance-test
  (let [acct (account :cash-account-status-opened)
        balances [{:credit 500 :debit 0}]]
    (testing "a non-zero balance bucket is rejected by default"
      (let [result (SUT/close-account acct
                                      balances
                                      [(policy-allowing
                                        :cash-account-action-close)])]
        (is (error/rejection? result))
        (is (= :cash-account/non-zero-on-close (error/kind result)))
        (is (= "acc.test" (:account-id (error/payload result))))))
    (testing "an explicit opt-out capability allows the close"
      (let [result (SUT/close-account acct
                                      balances
                                      [(policy-allowing
                                        :cash-account-action-close
                                        :cash-account-action-close-non-zero)])]
        (is (= :cash-account-status-closing (:account-status result)))))))

(deftest close-account-zero-balance-test
  (testing
    "balances that net to zero across all buckets close without the
           opt-out capability"
    (let [acct (account :cash-account-status-opened)
          balances [{:credit 500 :debit 500} {:credit 0 :debit 0}]
          result (SUT/close-account acct
                                    balances
                                    [(policy-allowing
                                      :cash-account-action-close)])]
      (is (= :cash-account-status-closing (:account-status result))))))

(deftest suspend-account-source-state-guard-test
  (testing
    "suspending an account not in :cash-account-status-opened is
           rejected, regardless of policy"
    (doseq [status [:cash-account-status-opening
                    :cash-account-status-closing
                    :cash-account-status-closed
                    :cash-account-status-suspended]]
      (let [result (SUT/suspend-account (account status) [])]
        (is (error/rejection? result))
        (is (= :cash-account/invalid-status (error/kind result)))
        (is (= status (:status (error/payload result))))))))

(deftest reopen-account-source-state-guard-test
  (testing
    "reopening an account not in :cash-account-status-suspended is
           rejected, regardless of policy"
    (doseq [status [:cash-account-status-opening
                    :cash-account-status-opened
                    :cash-account-status-closing
                    :cash-account-status-closed]]
      (let [result (SUT/reopen-account (account status) [])]
        (is (error/rejection? result))
        (is (= :cash-account/invalid-status (error/kind result)))
        (is (= status (:status (error/payload result))))))))

(defn- opened-account-with-address
  []
  (assoc (account :cash-account-status-opened)
         :bban "04000412345678"
         :payment-addresses [{:scheme :payment-address-scheme-scan
                              :scan {:sort-code "040004"
                                     :account-number "12345678"}}]))

(def ^:private product-version
  {:allowed-payment-address-schemes [:payment-address-scheme-scan]})

(deftest rotate-address-source-state-guard-test
  (testing
    "rotating an account not in :cash-account-status-opened is
           rejected, regardless of policy"
    (doseq [status [:cash-account-status-opening
                    :cash-account-status-closing
                    :cash-account-status-closed
                    :cash-account-status-suspended]]
      (let [result (SUT/rotate-address (account status)
                                       product-version
                                       (constantly "99999999")
                                       [])]
        (is (error/rejection? result))
        (is (= :cash-account/invalid-status (error/kind result)))
        (is (= status (:status (error/payload result))))))))

(deftest rotate-address-happy-test
  (testing
    "rotating replaces the payment address, rewrites the bban, and
           retires the old address on-record"
    (let [acct (opened-account-with-address)
          result (SUT/rotate-address acct
                                     product-version
                                     (constantly "99999999")
                                     [(policy-allowing
                                       :cash-account-action-rotate-address)])]
      (is (= :cash-account-status-opened (:account-status result)))
      (is (= "04000499999999" (:bban result)))
      (is (= [{:scheme :payment-address-scheme-scan
               :scan {:sort-code "040004" :account-number "99999999"}}]
             (:payment-addresses result)))
      (is (= (:payment-addresses acct)
             (mapv :address (:retired-payment-addresses result))))
      (is (every? int?
                  (map :retired-at (:retired-payment-addresses result)))))))
