(ns com.repldriven.mono.bank-test-projections.balances
  "Available-balance projections for the scenario runner. The real
  side reads through the same `bank-balance/get-balances` query path
  the API uses; the model side reads from a flat map. Both return
  `{model-acct-id -> int-pence}` so equality is direct."
  (:require
    [com.repldriven.mono.bank-balance.interface :as balance]))

(defn- available
  "Available balance for `account-id` in pence, derived by the
  production aggregator. Returns 0 if no balances exist for the
  account yet."
  [bank account-id]
  (or (-> (balance/get-balances bank account-id)
          :available-balance
          :value)
      0))

(defn project-balances
  "Reads available balance for every account in `id-mapping`
  (a `real-id -> model-id` map). Returns
  `{model-acct-id -> int-pence}`, the same shape as
  `project-model-balances` so equality is direct.

  The runner owns the side-table and decides which accounts are
  in scope; this function never enumerates the bank itself.
  That keeps the projection deterministic and lets it focus on
  what the current command-sequence touched."
  [bank id-mapping]
  (->> id-mapping
       (map (fn [[real-id model-id]]
              [model-id (available bank real-id)]))
       (into {})))

(defn project-model-balances
  "Reads available balances out of `model-state` (the
  `bank-test-model.interface/init-state` shape). Returns
  `{model-acct-id -> int-pence}`."
  [model-state]
  (-> (:accounts model-state)
      (update-vals :available)))
