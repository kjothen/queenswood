(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]
    [com.repldriven.mono.bank-cash-account-product.store :as store]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- get-policies
  ([txn bank-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn {:bank-id bank-id})))
  ([txn bank-id product-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn
                                      {:bank-id bank-id
                                       :cash-product-id product-id}))))

(defn- counts
  [txn bank-id]
  (let-nom>
    [total (store/count-by-org txn bank-id)]
    {:cash-account-product {#{:bank-id} total}}))

(defn new-product
  ([txn bank-id data]
   (new-product txn bank-id data {}))
  ([txn bank-id data opts]
   (let-nom>
     [policies (get-policies txn bank-id opts)
      aggregates (counts txn bank-id)
      template (store/get-template txn (:template-id data))
      version (domain/new-product bank-id template data aggregates policies)
      _ (store/save-version txn version)]
     version)))

(defn open-draft
  ([txn bank-id product-id data]
   (open-draft txn bank-id product-id data {}))
  ([txn bank-id product-id data opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn bank-id product-id opts)
         versions (store/get-versions txn
                                      bank-id
                                      {:product-id product-id})
         template (store/get-template txn (:template-id (first versions)))
         version (domain/new-version bank-id
                                     product-id
                                     versions
                                     template
                                     data
                                     policies)
         _ (store/save-version txn version)]
        version)))))

(defn update-draft
  ([txn bank-id product-id version-id data]
   (update-draft txn bank-id product-id version-id data {}))
  ([txn bank-id product-id version-id data opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn bank-id product-id opts)
         existing (store/get-version txn bank-id product-id version-id)
         template (store/get-template txn (:template-id existing))
         version (domain/update-version existing template data policies)
         _ (store/save-version txn version)]
        version)))))

(defn discard-draft
  ([txn bank-id product-id version-id]
   (discard-draft txn bank-id product-id version-id {}))
  ([txn bank-id product-id version-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn bank-id product-id opts)
         existing (store/get-version txn bank-id product-id version-id)
         discarded (domain/discard existing policies)
         _ (store/save-version txn discarded)]
        discarded)))))

(defn publish
  ([txn bank-id product-id version-id]
   (publish txn bank-id product-id version-id {}))
  ([txn bank-id product-id version-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn bank-id product-id opts)
         existing (store/get-version txn bank-id product-id version-id)
         published (domain/publish existing policies)
         _ (store/save-version txn published)]
        published)))))

(defn new-template
  [config data]
  (let [template (domain/new-template data)
        existing (when (:template-id data)
                   (store/get-template config (:template-id data)))
        template (cond-> template
                         (and (not (error/anomaly? existing))
                              (:created-at existing))
                         (assoc :created-at (:created-at existing)))]
    (let-nom> [_ (store/save-template config template)]
      template)))

(defn get-template
  [txn template-id]
  (store/get-template txn template-id))

(defn list-templates
  [txn]
  (let-nom>
    [templates (store/get-templates txn)]
    (->> templates
         (remove :internal)
         (sort-by :product-type)
         vec)))

(defn get-version
  [txn bank-id product-id version-id]
  (store/get-version txn bank-id product-id version-id))

(defn get-product
  [txn bank-id product-id]
  (let-nom>
    [versions (store/get-versions txn
                                  bank-id
                                  {:product-id product-id :limit 100})]
    {:product-id product-id
     :versions versions}))

(defn get-products
  "The public product listing for a bank. Internal products (e.g. the
  bank's own-funds house product, snapshotted `:internal` from their
  template) are excluded — a full-scan in-memory filter, fine while
  product cardinality per bank is low. Internal products remain
  reachable by id via `get-product`, which is what house-account
  opening and posting use."
  ([txn bank-id]
   (get-products txn bank-id nil))
  ([txn bank-id opts]
   (let-nom>
     [versions (store/get-versions txn bank-id opts)]
     {:items (->> versions
                  (partition-by :product-id)
                  (mapv (fn [vs]
                          {:product-id (:product-id (first vs))
                           :versions (vec vs)}))
                  (remove (fn [{:keys [versions]}]
                            (:internal (first versions)))))})))
