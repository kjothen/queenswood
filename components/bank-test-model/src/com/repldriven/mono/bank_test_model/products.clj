(ns com.repldriven.mono.bank-test-model.products
  "Product lifecycle command specs. Each product carries a
  `:versions` vector — a real product can have multiple drafts
  superseded by published versions and discarded ones, and the
  brick allows opening a fresh draft once the current one is
  resolved (published or discarded). Commands:

  - `:create-product` / `:create-savings-product` — allocates a
    new product with v1 in `:draft`.
  - `:publish-product` — flips the latest draft to `:published`.
  - `:open-draft` — appends a new draft when the latest version
    is no longer a draft (published or discarded). Models the
    `:cash-account-product/draft-already-exists` rejection by
    being ineligible while a draft is open.
  - `:discard-draft` — flips the latest draft to `:discarded`.

  `:update-draft` is intentionally NOT modelled — the model has
  no observable for the fields it mutates (name, valid-from,
  etc.), so a generated update is a no-op against model state.
  The brick's update-path rejection kinds (404 unknown,
  409 immutable) are pinned via EDN scenarios instead."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- new-product-state
  "Builds the per-product map with `[v1-draft]` preloaded."
  [org-id product-type interest-rate-bps]
  {:org org-id
   :product-type product-type
   :interest-rate-bps interest-rate-bps
   :versions [{:status :draft :number 1}]})

(def create-product
  "Creates a draft `:current` product (rate 0) in an existing org.
  Args are `[org-id]`. Always eligible once at least one org
  exists."
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state] (gen/tuple (gen/elements (state/known-orgs state))))
   :next-state
   (fn [state {[org-id] :args}]
     (let [prod-id (state/next-product-id state)]
       (-> state
           (assoc-in [:products prod-id] (new-product-state org-id :current 0))
           (update-in [:orgs org-id :products] (fnil conj []) prod-id)
           (update :next-product-id inc))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

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
                               (new-product-state org-id :savings rate-bps))
                     (update-in [:orgs org-id :products] (fnil conj []) prod-id)
                     (update :next-product-id inc))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

(defn- flip-latest
  "Replaces the latest version of `prod-id` in state with the
  result of `(f latest)`. Used by transitions that act on the
  highest-numbered version."
  [state prod-id f]
  (update-in state
             [:products prod-id :versions]
             (fn [versions]
               (conj (pop versions) (f (peek versions))))))

(def publish-product
  "Flips the latest draft on a product to `:published`. Args are
  `[prod-id]`. Eligible only when at least one product has a
  draft as its latest version."
  {:run? (fn [state] (seq (state/drafts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/drafts state))))
   :next-state
   (fn [state {[prod-id] :args}]
     (flip-latest state prod-id (fn [v] (assoc v :status :published))))
   :valid? (fn [state {[prod-id] :args}]
             (= :draft (:status (state/latest-version state prod-id))))})

(def discard-draft
  "Flips the latest draft on a product to `:discarded`. Args are
  `[prod-id]`. Eligible only when at least one product has a
  draft as its latest version (same set as `:publish-product`)."
  {:run? (fn [state] (seq (state/drafts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/drafts state))))
   :next-state
   (fn [state {[prod-id] :args}]
     (flip-latest state prod-id (fn [v] (assoc v :status :discarded))))
   :valid? (fn [state {[prod-id] :args}]
             (= :draft (:status (state/latest-version state prod-id))))})

(def open-draft
  "Appends a new draft to a product whose latest version is not a
  draft (i.e., currently `:published` or `:discarded`). Args are
  `[prod-id]`. The new version's number is `latest.number + 1`."
  {:run? (fn [state] (seq (state/open-draftable state)))
   :args (fn [state] (gen/tuple (gen/elements (state/open-draftable state))))
   :next-state (fn [state {[prod-id] :args}]
                 (let [latest (state/latest-version state prod-id)]
                   (update-in state
                              [:products prod-id :versions]
                              conj
                              {:status :draft :number (inc (:number latest))})))
   :valid? (fn [state {[prod-id] :args}]
             (let [latest (state/latest-version state prod-id)]
               (and latest (not= :draft (:status latest)))))})
