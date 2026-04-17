(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]
    [com.repldriven.mono.bank-cash-account-product.store :as store]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- draft?
  [version]
  (= :cash-account-product-version-status-draft (:status version)))

(defn- latest-version
  [versions]
  (->> versions
       (sort-by :version-number)
       last))

(defn new-product
  "Creates a cash account product as an initial draft v1.
  Returns {:version <map>} or anomaly."
  [txn org-id version-data]
  (let [product-id (encryption/generate-id "prd")
        version (domain/new-version org-id product-id 1 version-data)]
    (let-nom> [_ (store/save-version txn version)]
      {:version version})))

(defn upsert-draft
  "Creates or updates the current draft for a product. If
  the latest version is a draft, replaces its mutable
  fields with version-data (preserving :version-id and
  :version-number). If the latest version is published,
  creates a new draft with the next version-number.
  Returns {:version <map>} or anomaly."
  [txn org-id product-id version-data]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [versions (store/get-versions txn
                                     org-id
                                     {:product-id product-id})
        latest (latest-version versions)
        version (if (draft? latest)
                  (domain/update-version latest version-data)
                  (domain/new-version org-id
                                      product-id
                                      (inc (count versions))
                                      version-data))
        _ (store/save-version txn version)]
       {:version version}))))

(defn get-version
  "Loads a version by org-id, product-id, and version-id.
  Returns the version map or rejection anomaly if not
  found."
  [txn org-id product-id version-id]
  (store/get-version txn org-id product-id version-id))

(defn get-versions
  "Lists versions. With product-id, scans that product;
  without, scans all products for the org. Returns
  {:versions [<map> ...]} or anomaly."
  ([txn org-id]
   (let-nom> [versions (store/get-versions txn org-id)]
     {:versions versions}))
  ([txn org-id product-id]
   (let-nom> [versions (store/get-versions txn
                                           org-id
                                           {:product-id product-id
                                            :limit 100})]
     {:versions versions})))

(defn get-published-version
  "Returns the highest-version-number published version
  for a product, or nil if none published. Rejects if
  the product-id is unknown."
  [txn org-id product-id]
  (let-nom> [versions (store/get-versions txn
                                          org-id
                                          {:product-id product-id})]
    (->> versions
         (filter (fn [v]
                   (= :cash-account-product-version-status-published
                      (:status v))))
         (sort-by :version-number)
         last)))

(defn get-latest-version
  "Returns the highest-version-number version (any status)
  for a product. Rejects if the product-id is unknown."
  [txn org-id product-id]
  (let-nom> [versions (store/get-versions txn
                                          org-id
                                          {:product-id product-id})]
    (latest-version versions)))

(defn get-products
  "Returns {:versions [<map> ...]} with the latest version
  (any status) per product for the organization."
  [txn org-id]
  (let-nom> [versions (store/get-versions txn org-id)]
    {:versions (->> versions
                    (group-by :product-id)
                    vals
                    (mapv latest-version))}))

(defn publish
  "Publishes the current draft for the product. Rejects
  with :cash-account-product/no-draft if the latest
  version is not a draft. Returns the published version
  map or anomaly."
  [txn org-id product-id]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [versions (store/get-versions txn
                                     org-id
                                     {:product-id product-id})
        latest (latest-version versions)
        _ (when-not (draft? latest)
            (error/reject :cash-account-product/no-draft
                          {:message "No draft to publish"
                           :organization-id org-id
                           :product-id product-id}))
        published (domain/publish latest)
        _ (store/save-version txn published)]
       published))))
