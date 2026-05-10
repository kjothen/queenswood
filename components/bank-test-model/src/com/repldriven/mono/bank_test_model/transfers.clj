(ns com.repldriven.mono.bank-test-model.transfers
  (:require
    [com.repldriven.mono.bank-test-model.policy :as policy]
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- bump-legs
  [state & accts]
  (reduce (fn [s a] (update-in s [:accounts a :transaction-legs] (fnil inc 0)))
          state
          accts))

(defn- apply-delta
  [state acct delta]
  (let [pre (state/balance state acct)
        post (+ pre delta)]
    (if (policy/permits? (:policies state) :available pre post)
      (-> state
          (assoc-in [:accounts acct :available] post)
          (bump-legs acct))
      state)))

(defn- transfer-between
  [state from to amount]
  (let [pre-from (state/balance state from)
        post-from (- pre-from amount)
        pre-to (state/balance state to)
        post-to (+ pre-to amount)]
    (if (and (policy/permits? (:policies state) :available pre-from post-from)
             (policy/permits? (:policies state) :available pre-to post-to))
      (-> state
          (assoc-in [:accounts from :available] post-from)
          (assoc-in [:accounts to :available] post-to)
          (bump-legs from to))
      state)))

(def inbound-transfer
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 (let [marker (state/next-inbound-id state)
                       advanced (apply-delta state acct amount)]
                   (-> advanced
                       (update :inbound-payments conj marker)
                       (update :next-inbound-id inc))))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})

(def outbound-transfer
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 (apply-delta state acct (- amount)))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})

(def outbound-payment
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 ;; Reality rejects non-positive amounts via
                 ;; `:transaction/invalid-amount` — predict no-op.
                 (if-not (pos? amount)
                   state
                   (let [advanced (apply-delta state acct (- amount))]
                     (if (= advanced state)
                       state
                       (let [pmt-id (state/next-payment-id advanced)]
                         (-> advanced
                             (assoc-in
                              [:payments pmt-id]
                              {:debtor acct :amount amount :status :pending})
                             (update :next-payment-id inc)))))))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})

(def settle-outbound-payment
  {:run? (fn [state] (seq (state/pending-payments state)))
   :args (fn [state] (gen/tuple (gen/elements (state/pending-payments state))))
   :next-state (fn [state {[pmt-id] :args}]
                 (assoc-in state [:payments pmt-id :status] :completed))
   :valid? (fn [state {[pmt-id] :args}]
             (= :pending (get-in state [:payments pmt-id :status])))})

(defn- accounts-by-org
  "Returns a map of org-id → vector of account-ids for known
  accounts. Used to constrain `internal-transfer` to same-org
  pairs (the production API enforces same-org)."
  [state]
  (group-by (fn [a] (get-in state [:accounts a :org]))
            (state/known-accounts state)))

(def internal-transfer
  {:run? (fn [state]
           (boolean (some (fn [[_ accts]] (>= (count accts) 2))
                          (accounts-by-org state))))
   :args (fn [state]
           (let [groups (->> (accounts-by-org state)
                             vals
                             (filter (fn [accts] (>= (count accts) 2))))]
             (gen/let [accts (gen/elements groups)
                       from (gen/elements accts)
                       to (gen/such-that (fn [a] (not= a from))
                                         (gen/elements accts))
                       amount (gen/choose 1 10000)]
               [from to amount])))
   :next-state (fn [state {[from to amount] :args}]
                 ;; Generator is constrained to positive amounts,
                 ;; same-org pairs, and distinct accounts. Explicit
                 ;; scenarios may still pass cross-org / self / zero
                 ;; / negative cases that reality rejects (see the
                 ;; *-rejected.edn fixtures). The model predicts a
                 ;; no-op for any rejection-bound input.
                 (if (and (pos? amount)
                          (not= from to)
                          (= (get-in state [:accounts from :org])
                             (get-in state [:accounts to :org])))
                   (transfer-between state from to amount)
                   state))
   :valid? (fn [state {[from to] :args}]
             (and (contains? (:accounts state) from)
                  (contains? (:accounts state) to)))})
