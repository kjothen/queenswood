(ns com.repldriven.mono.bank-test-model.interface
  "Pure-functional model of the bank's domain rules. Re-implementation
  of the relevant production logic in plain Clojure data, used by the
  scenario runner to compare model state against real-system state.

  Imports nothing from production components. See
  `docs/design/scenario-testing.md`."
  (:require
    [com.repldriven.mono.bank-test-model.balances :as balances]
    [com.repldriven.mono.bank-test-model.fees :as fees]
    [com.repldriven.mono.bank-test-model.state :as state]
    [com.repldriven.mono.bank-test-model.transfers :as transfers]))

(def model
  "Fugato-shape model: a map keyed by command keyword. Each entry
  carries `:run?`, `:args`, `:next-state`, and (where helpful)
  `:valid?` — see `docs/design/scenario-testing.md`."
  {:open-account balances/open-account
   :inbound-transfer transfers/inbound-transfer
   :outbound-transfer transfers/outbound-transfer
   :apply-fee fees/apply-fee})

(def init-state state/init-state)

(def known-accounts state/known-accounts)

(def balance state/balance)
