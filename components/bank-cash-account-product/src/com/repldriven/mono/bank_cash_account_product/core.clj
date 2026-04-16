(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]
    [com.repldriven.mono.bank-cash-account-product.store :as store]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn new-product
  "Creates a cash account product as an initial draft v1.
  Returns {:version <map>} or anomaly."
  [txn org-id version-data]
  (let [product-id (encryption/generate-id "prd")
        version (domain/new-version org-id product-id 1 version-data)]
    (let-nom> [_ (store/save-version txn version)]
      {:version version})))

(defn new-version
  "Creates a new draft version for a product. Computes the
  next version-number from existing versions. Rejects if
  the latest version is still a draft. Returns
  {:version <map>} or anomaly."
  [txn org-id product-id version-data]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [versions (store/get-versions txn
                                     org-id
                                     {:product-id product-id})
        latest (->> versions
                    (sort-by :version-number)
                    last)
        _ (when (and latest
                     (= :cash-account-product-version-status-draft
                        (:status latest)))
            (error/reject
             :cash-account-product/draft-exists
             {:message (str "Cannot create a new version while the "
                            "latest version is still a draft")}))
        next-num (inc (count versions))
        version (domain/new-version org-id
                                    product-id
                                    next-num
                                    version-data)
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

(defn get-published
  "Returns the highest-version-number published version
  for a product, or nil if none published."
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

(defn publish
  "Publishes a draft version. Returns the published
  version map or anomaly."
  [txn org-id product-id version-id]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [version (store/get-version txn org-id product-id version-id)
        _ (when-not (= :cash-account-product-version-status-draft
                       (:status version))
            (error/reject :cash-account-product/not-draft
                          {:message "Only draft versions can be published"}))
        published (domain/publish version)
        _ (store/save-version txn published)]
       published))))
