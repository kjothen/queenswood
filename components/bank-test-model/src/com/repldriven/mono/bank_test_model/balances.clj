(ns com.repldriven.mono.bank-test-model.balances
  "Org/account command specs for the model — fugato shape, pure
  functions only.

  - `:create-org` — Queenswood-side: onboards a tenant. Bundles the
    new org, its auto-published settlement product, the auto-active
    organization party, and the org's settlement account, all in
    one transaction. Mirrors `bank-organization/new-organization`.
  - `:close-account` — closes the (settlement) account. Eligible
    once at least one open account exists.

  `:open-account` — opening an account for an end-customer (person
  party) — is intentionally NOT modelled. The real flow needs an
  active person party + a published tenant product + the tenant's
  org, which is a heavy precondition chain for what amounts to
  exercising balance arithmetic the settlement account already
  covers. The verb is still callable from EDN scenarios that
  curate those preconditions explicitly."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def create-org
  "Allocates the next synthetic org id, registers the org with its
  auto-created (already-published) settlement product *and* its
  auto-active organization party, and opens the settlement account
  at zero `:available` balance against both. The settlement
  account is recorded as the org's `:settlement-account` so
  `:accrue-interest` knows where to debit `interest-payable`."
  {:args (fn [_state] (gen/return []))
   :next-state (fn [state _command]
                 (let [org-id (state/next-org-id state)
                       acct-id (state/next-id state)
                       prod-id (state/next-product-id state)
                       party-id (state/next-party-id state)]
                   (-> state
                       (assoc-in [:accounts acct-id]
                                 {:available 0
                                  :credit-carry 0
                                  :interest-accrued 0
                                  :status :open
                                  :org org-id
                                  :product prod-id
                                  :party party-id})
                       (assoc-in [:orgs org-id]
                                 {:accounts [acct-id]
                                  :products [prod-id]
                                  :parties [party-id]
                                  :settlement-account acct-id})
                       (assoc-in [:products prod-id]
                                 {:org org-id
                                  :product-type :settlement
                                  :interest-rate-bps 0
                                  :versions [{:status :published :number 1}]})
                       (assoc-in
                        [:parties party-id]
                        {:org org-id :type :organization :status :active})
                       (update :next-id inc)
                       (update :next-org-id inc)
                       (update :next-product-id inc)
                       (update :next-party-id inc))))})

(def close-account
  "Closes an open account. Args are `[acct-id]`. Eligible only when
  at least one open account exists; the args generator picks one.
  Closed accounts are skipped by `:accrue-interest` /
  `:capitalize-interest` (which already filter to `:open`) but
  still appear in projections — closure flips status, not
  membership."
  {:run? (fn [state] (seq (state/open-accounts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/open-accounts state))))
   :next-state (fn [state {[acct-id] :args}]
                 (assoc-in state [:accounts acct-id :status] :closed))
   :valid? (fn [state {[acct-id] :args}]
             (= :open (get-in state [:accounts acct-id :status])))})
