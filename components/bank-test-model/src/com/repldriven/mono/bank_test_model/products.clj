(ns com.repldriven.mono.bank-test-model.products
  "Product lifecycle command specs — `:create-product` makes a
  draft attached to an org, `:publish-product` flips a draft to
  published. The model treats products as opaque ids carrying a
  status; the runner translates them into real
  `bank-cash-account-product/new-product` and `.../publish` calls."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def create-product
  "Creates a draft `:current` product (rate 0) in an existing org.
  Args are `[org-id]`. Always eligible once at least one org
  exists."
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state] (gen/tuple (gen/elements (state/known-orgs state))))
   :next-state (fn [state {[org-id] :args}]
                 (let [prod-id (state/next-product-id state)]
                   (->
                     state
                     (assoc-in [:products prod-id]
                               {:org org-id
                                :status :draft
                                :product-type :current
                                :interest-rate-bps 0})
                     (update-in [:orgs org-id :products] (fnil conj []) prod-id)
                     (update :next-product-id inc))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

(def publish-product
  "Transitions a draft product to `:published`. Args are
  `[prod-id]`. Eligible only when at least one draft exists."
  {:run? (fn [state] (seq (state/drafts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/drafts state))))
   :next-state (fn [state {[prod-id] :args}]
                 (assoc-in state [:products prod-id :status] :published))
   :valid? (fn [state {[prod-id] :args}]
             (= :draft (get-in state [:products prod-id :status])))})

(def create-savings-product
  "Creates a draft `:savings` product carrying the given
  interest-rate-bps. Args are `[org-id rate-bps]`. The rate
  flows through to `:accrue-interest`'s `daily-interest`
  calculation."
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state]
           (gen/let [org (gen/elements (state/known-orgs state))
                     rate (gen/choose 100 10000)]
             [org rate]))
   :next-state (fn [state {[org-id rate-bps] :args}]
                 (let [prod-id (state/next-product-id state)]
                   (->
                     state
                     (assoc-in [:products prod-id]
                               {:org org-id
                                :status :draft
                                :product-type :savings
                                :interest-rate-bps rate-bps})
                     (update-in [:orgs org-id :products] (fnil conj []) prod-id)
                     (update :next-product-id inc))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})
