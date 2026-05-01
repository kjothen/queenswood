(ns com.repldriven.mono.bank-test-model.balances
  "Org/account command specs for the model — fugato shape, pure
  functions only.

  Two commands:

  - `:open-account` — creates a new customer org plus its first
    default account, allocating the org's auto-published settlement
    product *and* the auto-organization party that owns the
    account. Mirrors what `bank-organization/new-organization` does
    in production: org + party (active) + product (published) +
    first account, all in one transaction.
  - `:add-account` — opens an additional account inside an
    existing org. Args are `[org-id party-id prod-id]` — the party
    must belong to the org and be `:active`, and the product must
    belong to the org and be `:published`."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def open-account
  "Allocates the next synthetic org id, registers the org with its
  auto-created (already-published) settlement product *and* its
  auto-active organization party, and opens a default account at
  zero `:available` balance against both. The first account in
  the org is recorded as the org's `:settlement-account` so
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
                                  :status :published
                                  :product-type :settlement
                                  :interest-rate-bps 0})
                       (assoc-in
                        [:parties party-id]
                        {:org org-id :type :organization :status :active})
                       (update :next-id inc)
                       (update :next-org-id inc)
                       (update :next-product-id inc)
                       (update :next-party-id inc))))})

(def add-account
  "Opens an additional account inside an existing org for an active
  party against a published product. The args generator picks an
  `[org party prod]` triple from the eligible set."
  {:run? (fn [state] (seq (state/add-account-options state)))
   :args (fn [state]
           (gen/let [[org-id party-id prod-id]
                     (gen/elements (state/add-account-options state))]
             [org-id party-id prod-id]))
   :next-state (fn [state {[org-id party-id prod-id] :args}]
                 (let [acct-id (state/next-id state)]
                   (-> state
                       (assoc-in [:accounts acct-id]
                                 {:available 0
                                  :credit-carry 0
                                  :interest-accrued 0
                                  :org org-id
                                  :product prod-id
                                  :party party-id})
                       (update-in [:orgs org-id :accounts] conj acct-id)
                       (update :next-id inc))))
   :valid?
   (fn [state {[org-id party-id prod-id] :args}]
     (and (contains? (:orgs state) org-id)
          (= :published (get-in state [:products prod-id :status]))
          (contains? (set (get-in state [:orgs org-id :products])) prod-id)
          (= :active (get-in state [:parties party-id :status]))
          (contains? (set (get-in state [:orgs org-id :parties])) party-id)))})
