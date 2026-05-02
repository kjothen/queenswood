(ns com.repldriven.mono.bank-test-model.transfers
  "Inbound, outbound, and internal transfer command specs. Each
  consults the pure available-balance policy helper and leaves state
  unchanged when the rule denies the transition. The internal-
  transfer case checks both legs and either applies both or neither —
  mirroring production's atomic `apply-legs`."
  (:require
    [com.repldriven.mono.bank-test-model.policy :as policy]
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- bump-legs
  "Increments `:transaction-legs` for each named account. The
  counter mirrors what `bank-transaction/get-transactions` returns
  for that account — one leg per appearance in a posted
  transaction."
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
  "Atomically moves `amount` from `from` to `to`. Either both legs
  pass the available-balance rule (state advances), or one fails
  (state is unchanged)."
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
  "Credits an existing account by a positive amount via the scheme
  settlement path — production receives a `transaction-settled`
  credit event, the brick records the transaction, posts balances,
  and persists an InboundPayment keyed by `scheme-transaction-id`.
  The model tracks each settled stx-marker in `:inbound-payments`
  for projection equality; the runner translates the marker into
  a deterministic stx-id string."
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

(def outbound-payment
  "Same balance shape as `:outbound-transfer` (debit customer, credit
  settlement-suspense), but routed in production through
  `bank-payment/submit-outbound` — which also adds an
  `OutboundPayment` record (status `:pending`) and publishes a
  scheme command. The model tracks the payment under
  `:payments :pmt-N` so projections can compare against
  `bank-payment/get-outbound-payment` on the real side."
  {:run? (fn [state] (seq (state/known-accounts state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-accounts state))
                      (gen/choose 1 10000)))
   :next-state (fn [state {[acct amount] :args}]
                 (let [advanced (apply-delta state acct (- amount))]
                   (if (= advanced state)
                     ;; debit denied — no payment record posted in
                     ;; production either (apply-legs short-circuits)
                     state
                     (let [pmt-id (state/next-payment-id advanced)]
                       (-> advanced
                           (assoc-in
                            [:payments pmt-id]
                            {:debtor acct :amount amount :status :pending})
                           (update :next-payment-id inc))))))
   :valid? (fn [state {[acct] :args}] (contains? (:accounts state) acct))})

(def settle-outbound-payment
  "Flips a `:pending` outbound payment to `:completed`. Args are
  `[pmt-id]`. Production receives a `transaction-settled` debit
  event with `:end-to-end-id` matching our payment-id; the brick's
  settle-outbound handler updates status. No balance change at
  settlement — the customer debit and suspense credit happen at
  submit time."
  {:run? (fn [state] (seq (state/pending-payments state)))
   :args (fn [state] (gen/tuple (gen/elements (state/pending-payments state))))
   :next-state (fn [state {[pmt-id] :args}]
                 (assoc-in state [:payments pmt-id :status] :completed))
   :valid? (fn [state {[pmt-id] :args}]
             (= :pending (get-in state [:payments pmt-id :status])))})

(def internal-transfer
  "Moves `amount` between two distinct accounts. Atomic — both legs
  pass the available rule, or neither applies. Mirrors production's
  `apply-legs`, which evaluates the available rule per affected
  account inside one FDB transaction."
  {:run? (fn [state] (>= (count (state/known-accounts state)) 2))
   :args (fn [state]
           (let [accts (state/known-accounts state)]
             (gen/let [from (gen/elements accts)
                       to (gen/such-that (fn [a] (not= a from))
                                         (gen/elements accts))
                       amount (gen/choose 1 10000)]
               [from to amount])))
   :next-state (fn [state {[from to amount] :args}]
                 (transfer-between state from to amount))
   :valid? (fn [state {[from to] :args}]
             (and (contains? (:accounts state) from)
                  (contains? (:accounts state) to)
                  (not= from to)))})
