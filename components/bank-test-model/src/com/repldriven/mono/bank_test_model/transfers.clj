(ns com.repldriven.mono.bank-test-model.transfers
  "Inbound and outbound transfer command specs. Both consult the
  pure available-balance policy helper and leave state unchanged
  when the rule denies the transition."
  (:require
    [com.repldriven.mono.bank-test-model.policy :as policy]
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- apply-delta
  [state acct delta]
  (let [pre (state/balance state acct)
        post (+ pre delta)]
    (if (policy/permits? (:policies state) :available pre post)
      (assoc-in state [:accounts acct :available] post)
      state)))

(def inbound-transfer
  "Credits an existing account by a positive amount. Always permitted
  in practice (post >= pre), but routed through the rule for
  symmetry with outbound."
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 (apply-delta state acct amount))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})

(def outbound-transfer
  "Debits an existing account by a positive amount. Strict denial
  when the move would push available below zero, with the lenient
  `improving?` allowance handling already-negative accounts."
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 (apply-delta state acct (- amount)))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})
