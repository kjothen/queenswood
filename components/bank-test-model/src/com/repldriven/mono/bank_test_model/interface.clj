(ns com.repldriven.mono.bank-test-model.interface
  "Pure-functional model of the bank's domain rules. Re-implementation
  of the relevant production logic in plain Clojure data, used by the
  scenario runner to compare model state against real-system state.

  Imports nothing from production components. See
  `docs/design/scenario-testing.md`."
  (:require
    [com.repldriven.mono.bank-test-model.balances :as balances]
    [com.repldriven.mono.bank-test-model.fees :as fees]
    [com.repldriven.mono.bank-test-model.interest :as interest]
    [com.repldriven.mono.bank-test-model.parties :as parties]
    [com.repldriven.mono.bank-test-model.products :as products]
    [com.repldriven.mono.bank-test-model.state :as state]
    [com.repldriven.mono.bank-test-model.transfers :as transfers]))

(def model
  "Fugato-shape model: a map keyed by command keyword. Each entry
  carries `:run?`, `:args`, `:next-state`, and (where helpful)
  `:valid?` — see `docs/tdd/scenario-testing.md`."
  {:open-account balances/open-account
   :add-account balances/add-account
   :close-account balances/close-account
   :create-product products/create-product
   :create-savings-product products/create-savings-product
   :publish-product products/publish-product
   :open-draft products/open-draft
   :discard-draft products/discard-draft
   :create-person-party parties/create-person-party
   :activate-party parties/activate-party
   :inbound-transfer transfers/inbound-transfer
   :outbound-transfer transfers/outbound-transfer
   :outbound-payment transfers/outbound-payment
   :internal-transfer transfers/internal-transfer
   :apply-fee fees/apply-fee
   :accrue-interest interest/accrue-interest
   :capitalize-interest interest/capitalize-interest})

(def init-state state/init-state)

(def known-accounts state/known-accounts)

(def known-orgs state/known-orgs)

(def balance state/balance)
