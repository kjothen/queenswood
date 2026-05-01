(ns com.repldriven.mono.bank-test-model.state
  "Initial model state and pure helpers over it. State is a flat map
  of accounts keyed by synthetic account-id (`:acct-0`, `:acct-1`,
  …), a `:next-id` counter the runner uses to allocate, the active
  policy set in model-shape, and a `:now` clock advanced by future
  time-travel commands.

  Why `:acct-<n>` rather than `:acct/<n>` — Clojure's reader rejects
  namespaced keywords whose name starts with a digit (`:acct/0` won't
  parse), and tests are easier to read with literal forms. The
  runner treats these as opaque side-table keys; the format doesn't
  leak.")

(def init-state
  "Empty bank, with the available-balance rule active. The `:policies`
  map mirrors the production policy set in model-shape — a hand-built
  re-implementation, not anything imported from `bank-policy`. Update
  this when the production policies change."
  {:accounts {}
   :policies {:available {:min 0 :improving? true}}
   :next-id 0
   :now 0})

(defn next-id
  "The next synthetic account id, given current state. Pure — does
  not advance the counter."
  [state]
  (keyword (str "acct-" (:next-id state))))

(defn known-accounts
  "Accounts the model knows about, as a vector of synthetic ids."
  [state]
  (vec (keys (:accounts state))))

(defn balance
  "Available balance for `acct`, or 0 if the account is unknown."
  [state acct]
  (get-in state [:accounts acct :available] 0))
