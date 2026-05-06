(ns com.repldriven.mono.bank-test-model.fees
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def apply-fee
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 (-> state
                     (update-in [:accounts acct :available] (fnil - 0) amount)
                     (update-in [:accounts acct :transaction-legs]
                                (fnil inc 0))))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})
