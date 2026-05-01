(ns com.repldriven.mono.bank-test-model.fees
  "Fee command spec. Fees post regardless of the available-balance
  rule — production's `transaction_type` filter on the
  available-limit excludes `:transaction-type-fee`. This is the
  mechanism that drives accounts into breach so curative-improving
  paths get tested by the inbound spec later."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def apply-fee
  "Debits an existing account by a positive amount, bypassing the
  policy check. Always advances state. Bumps `:transaction-legs`
  on the account."
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
