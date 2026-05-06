(ns com.repldriven.mono.bank-test-model.products
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- new-product-state
  [org-id product-type interest-rate-bps]
  {:org org-id
   :product-type product-type
   :interest-rate-bps interest-rate-bps
   :versions [{:status :draft :number 1}]})

(def create-product
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state]
           (gen/let [org (gen/elements (state/known-orgs state))
                     type (gen/elements [:current :savings])
                     rate (gen/choose 100 10000)]
             [org type (if (= :savings type) rate 0)]))
   :next-state (fn [state {[org-id type rate-bps] :args}]
                 (let [prod-id (state/next-product-id state)]
                   (->
                     state
                     (assoc-in [:products prod-id]
                               (new-product-state org-id type rate-bps))
                     (update-in [:orgs org-id :products] (fnil conj []) prod-id)
                     (update :next-product-id inc))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

(defn- flip-latest
  [state prod-id f]
  (update-in state
             [:products prod-id :versions]
             (fn [versions]
               (conj (pop versions) (f (peek versions))))))

(def publish-product
  {:run? (fn [state] (seq (state/drafts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/drafts state))))
   :next-state
   (fn [state {[prod-id] :args}]
     (flip-latest state prod-id (fn [v] (assoc v :status :published))))
   :valid? (fn [state {[prod-id] :args}]
             (= :draft (:status (state/latest-version state prod-id))))})

(def discard-draft
  {:run? (fn [state] (seq (state/drafts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/drafts state))))
   :next-state
   (fn [state {[prod-id] :args}]
     (flip-latest state prod-id (fn [v] (assoc v :status :discarded))))
   :valid? (fn [state {[prod-id] :args}]
             (= :draft (:status (state/latest-version state prod-id))))})

(def open-draft
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
