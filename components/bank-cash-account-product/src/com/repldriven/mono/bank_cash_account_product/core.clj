(ns com.repldriven.mono.bank-cash-account-product.core
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as domain]
    [com.repldriven.mono.bank-cash-account-product.store :as store]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- draft?
  [version]
  (= :cash-account-product-version-status-draft (:status version)))

(defn new-product
  "Creates a cash account product as an initial draft v1. Returns
  the version map or anomaly — `domain/new-version` rejects with
  `:cash-account-product/duplicate-items` when the data's
  repeated-value fields contain duplicates."
  [txn org-id version-data]
  (let [product-id (utility/generate-id "prd")]
    (let-nom>
      [version (domain/new-version org-id product-id 1 version-data)
       _ (store/save-version txn version)]
      version)))

(defn open-draft
  "Opens a new draft version for an existing product. Rejects
  `:cash-account-product/draft-already-exists` if any version is
  currently in draft state — discarded and published versions do
  not count. Returns the newly-created version map or anomaly."
  [txn org-id product-id version-data]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [versions (store/get-versions txn
                                     org-id
                                     {:product-id product-id})
        _ (when (some draft? versions)
            (error/reject :cash-account-product/draft-already-exists
                          {:message "A draft already exists"
                           :organization-id org-id
                           :product-id product-id}))
        version (domain/new-version org-id
                                    product-id
                                    (inc (count versions))
                                    version-data)
        _ (store/save-version txn version)]
       version))))

(defn- load-draft-or-reject
  "Loads an existing version by vid and rejects unless it's in draft
  state. Returns the version map, a :version-not-found rejection,
  or a :version-immutable rejection."
  [txn org-id product-id version-id]
  (let-nom>
    [existing (store/get-version txn org-id product-id version-id)
     _ (when-not (draft? existing)
         (error/reject :cash-account-product/version-immutable
                       {:message
                        "Version is not a draft and cannot be modified"
                        :organization-id org-id
                        :product-id product-id
                        :version-id version-id
                        :status (:status existing)}))]
    existing))

(defn update-draft
  "Updates an existing draft version in place. Preserves identity
  fields and bumps :updated-at. Rejects
  `:cash-account-product/version-not-found` if the vid is unknown,
  `:cash-account-product/version-immutable` if the vid exists but
  isn't in draft state, or `:cash-account-product/duplicate-items`
  for non-unique repeated fields."
  [txn org-id product-id version-id version-data]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [existing (load-draft-or-reject txn org-id product-id version-id)
        version (domain/update-version existing version-data)
        _ (store/save-version txn version)]
       version))))

(defn discard-draft
  "Discards an existing draft version — transitions its state to
  discarded and stamps :discarded-at. Rejects
  `:cash-account-product/version-not-found` or
  `:cash-account-product/version-immutable` as `update-draft` does.
  Returns the discarded version map on success."
  [txn org-id product-id version-id]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [existing (load-draft-or-reject txn org-id product-id version-id)
        discarded (domain/discard existing)
        _ (store/save-version txn discarded)]
       discarded))))

(defn publish
  "Publishes an existing draft version. Rejects
  `:cash-account-product/version-not-found` or
  `:cash-account-product/version-immutable` if the vid isn't a
  draft. Older published versions stay `:published` — callers treat
  the highest-version-number published record as \"current
  published\" when they need a single answer."
  [txn org-id product-id version-id]
  (store/transact
   txn
   (fn [txn]
     (let-nom>
       [existing (load-draft-or-reject txn org-id product-id version-id)
        published (domain/publish existing)
        _ (store/save-version txn published)]
       published))))

(defn get-version
  "Loads a single version by (org-id, product-id, version-id)."
  [txn org-id product-id version-id]
  (store/get-version txn org-id product-id version-id))

(defn get-product
  "Returns `{:product-id <pid> :versions [...]}` with the product's
  full version history. Versions come back newest-first via the
  store's default desc scan. Rejects
  `:cash-account-product/product-not-found` if no versions exist."
  [txn org-id product-id]
  (let-nom>
    [versions (store/get-versions txn
                                  org-id
                                  {:product-id product-id :limit 100})]
    {:product-id product-id
     :versions versions}))

(defn get-products
  "Returns `{:items [...]}` where each item is the aggregate shape
  produced by `get-product` — one entry per distinct product-id
  owned by `org-id`. Versions inside each item are newest-first
  (inherited from the store's default `:order :desc` scan).

  opts are passed through to `store/get-versions`; in particular
  `:order` controls the scan direction, which (because the PK is
  `[org-id, product-id, version-id]` and FDB compares tuples
  lexicographically) also determines the order in which products
  appear in `:items`:

    :desc (default) — products newest-first by product-id, each
                      with versions newest-first
    :asc            — products oldest-first, each with versions
                      oldest-first

  `partition-by` is used in place of `group-by` so the scan's
  native product-id order is preserved — `group-by` would shuffle
  the top-level entries through a hash map.

  Pagination (cursors / limit) is not applied here yet — the
  bank-api caller truncates and builds links as needed."
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
