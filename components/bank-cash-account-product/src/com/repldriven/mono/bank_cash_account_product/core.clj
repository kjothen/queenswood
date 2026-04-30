(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]
    [com.repldriven.mono.bank-cash-account-product.store :as store]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn- get-policies
  ([txn org-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn {:organization-id org-id})))
  ([txn org-id product-id opts]
   (or (:policies opts)
       (policy/get-effective-policies txn
                                      {:organization-id org-id
                                       :cash-product-id product-id}))))

(defn- counts
  "Builds the cash-account-product aggregates map for the limit
  checks in `domain/new-product`. Each entry is keyed by the
  set of dimensions the count is grouped on."
  [txn org-id]
  (let-nom>
    [total (store/count-by-org txn org-id)]
    {:cash-account-product {#{:organization-id} total}}))

(defn new-product
  "Creates a product as an initial draft v1. opts supports
  `:policies` to override policy resolution."
  ([txn org-id data]
   (new-product txn org-id data {}))
  ([txn org-id data opts]
   (let-nom>
     [policies (get-policies txn org-id opts)
      aggregates (counts txn org-id)
      version (domain/new-product org-id data aggregates policies)
      _ (store/save-version txn version)]
     version)))

(defn open-draft
  "Opens a new draft version for an existing product. opts
  supports `:policies` to override policy resolution."
  ([txn org-id product-id data]
   (open-draft txn org-id product-id data {}))
  ([txn org-id product-id data opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn org-id product-id opts)
         versions (store/get-versions txn
                                      org-id
                                      {:product-id product-id})
         version (domain/new-version org-id
                                     product-id
                                     versions
                                     data
                                     policies)
         _ (store/save-version txn version)]
        version)))))

(defn update-draft
  "Updates an existing draft version in place. opts supports
  `:policies` to override policy resolution."
  ([txn org-id product-id version-id data]
   (update-draft txn org-id product-id version-id data {}))
  ([txn org-id product-id version-id data opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn org-id product-id opts)
         existing (store/get-version txn org-id product-id version-id)
         version (domain/update-version existing data policies)
         _ (store/save-version txn version)]
        version)))))

(defn discard-draft
  "Discards an existing draft version. opts supports
  `:policies` to override policy resolution."
  ([txn org-id product-id version-id]
   (discard-draft txn org-id product-id version-id {}))
  ([txn org-id product-id version-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn org-id product-id opts)
         existing (store/get-version txn org-id product-id version-id)
         discarded (domain/discard existing policies)
         _ (store/save-version txn discarded)]
        discarded)))))

(defn publish
  "Publishes an existing draft version. opts supports
  `:policies` to override policy resolution."
  ([txn org-id product-id version-id]
   (publish txn org-id product-id version-id {}))
  ([txn org-id product-id version-id opts]
   (store/transact
    txn
    (fn [txn]
      (let-nom>
        [policies (get-policies txn org-id product-id opts)
         existing (store/get-version txn org-id product-id version-id)
         published (domain/publish existing policies)
         _ (store/save-version txn published)]
        published)))))

(defn get-version
  "Loads a single version."
  [txn org-id product-id version-id]
  (store/get-version txn org-id product-id version-id))

(defn get-product
  "Returns `{:product-id <pid> :versions [...]}` for one product."
  [txn org-id product-id]
  (let-nom>
    [versions (store/get-versions txn
                                  org-id
                                  {:product-id product-id :limit 100})]
    {:product-id product-id
     :versions versions}))

(defn get-products
  "Returns `{:items [{:product-id ... :versions [...]} ...]}`,
  grouped by product-id in scan order."
  ([txn org-id]
   (get-products txn org-id nil))
  ([txn org-id opts]
   (let-nom>
     [versions (store/get-versions txn org-id opts)]
     {:items (->> versions
                  (partition-by :product-id)
                  (mapv (fn [vs]
                          {:product-id (:product-id (first vs))
                           :versions (vec vs)})))})))
