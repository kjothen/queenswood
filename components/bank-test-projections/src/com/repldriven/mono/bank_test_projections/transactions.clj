(ns com.repldriven.mono.bank-test-projections.transactions
  "Per-account transaction-leg count. Each transfer / fee /
  outbound-payment posts a transaction with one or more legs;
  `bank-transaction/get-transactions` returns the legs touching a
  given account. Counting them per account catches cases where a
  verb silently failed to record a transaction (or recorded
  duplicates, or hit the wrong account).

  Outbound-payment record projection (status, amount per payment)
  is a follow-up — `bank-payment` only exposes `get-by-id` today,
  so projecting the set requires the runner to track payment-ids."
  (:require
    [com.repldriven.mono.bank-transaction.interface :as transactions]))

(defn project-transactions
  "For each tracked account, counts the transaction legs touching
  it. Returns `{model-acct-id leg-count}`."
  [bank id-mapping]
  (->> id-mapping
       (map (fn [[real-id model-id]]
              [model-id (count (transactions/get-transactions bank real-id))]))
       (into {})))

(defn project-model-transactions
  "Reads `:transaction-legs` (a counter) off each account in
  model state. Returns `{model-acct-id leg-count}`."
  [model-state]
  (->> (:accounts model-state)
       (map (fn [[acct-id acct]]
              [acct-id (or (:transaction-legs acct) 0)]))
       (into {})))
