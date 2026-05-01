(ns com.repldriven.mono.bank-test-model.balances
  "Account-creation command spec for the model — fugato shape, pure
  functions only."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def open-account
  "Allocates the next synthetic id and registers the account at a
  zero `:available` balance. Always eligible — the model has no upper
  bound on accounts in Phase 1."
  {:args (fn [_state] (gen/return []))
   :next-state (fn [state _command]
                 (let [id (state/next-id state)]
                   (-> state
                       (assoc-in [:accounts id] {:available 0})
                       (update :next-id inc))))})
