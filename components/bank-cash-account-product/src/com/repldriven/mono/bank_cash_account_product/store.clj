(ns com.repldriven.mono.bank-cash-account-product.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "cash-account-products")

(def transact fdb/transact)

(defn find-product-by-gl-code
  "Return the first CashAccountProduct whose top-level (denormalised)
  `gl_code` matches the given code for this bank, or nil. The
  denormalised field mirrors `general_ledger.gl_code` from the kind
  variant; only GL products carry it. Used by bank-chart-of-accounts
  and by cash-account opening to resolve a GL account from its code."
  [txn bank-id gl-code]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/query-record-compound
              (fdb/open txn store-name)
              "CashAccountProduct"
              [["bank_id" bank-id]
               ["gl_code" gl-code]]
              {:index "CashAccountProduct_by_bank_gl_code"})
             schema/pb->CashAccountProduct))
   :cash-account-product/find-by-gl-code
   "Failed to find product by gl-code"))

(defn save-version
  [txn version]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/CashAccountProduct->java version)))
   :cash-account-product/save-version
   "Failed to save product version"))

(defn get-version
  [txn bank-id product-id version-id]
  (fdb/transact
   txn
   (fn [txn]
     (if-let [record (fdb/load-record (fdb/open txn store-name)
                                      bank-id
                                      product-id
                                      version-id)]
       (schema/pb->CashAccountProduct record)
       (error/reject :cash-account-product/version-not-found
                     {:message "Version not found"
                      :bank-id bank-id
                      :product-id product-id
                      :version-id version-id})))
   :cash-account-product/get-version
   "Failed to load product version"))

(defn count-by-org
  [txn bank-id]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/count-groups (fdb/open txn store-name)
                       "CashAccountProduct_count_by_bank"
                       [bank-id]))
   :cash-account-product/count-by-org
   {:message "Failed to count products by org"
    :bank-id bank-id}))

(defn get-versions
  ([txn bank-id]
   (get-versions txn bank-id nil))
  ([txn bank-id opts]
   (fdb/transact
    txn
    (fn [txn]
      (let [{:keys [product-id limit order]
             :or {limit 1000 order :desc}}
            opts
            prefix (if product-id [bank-id product-id] [bank-id])
            versions
            (mapv schema/pb->CashAccountProduct
                  (:records (fdb/scan-records (fdb/open txn store-name)
                                              {:prefix prefix
                                               :limit limit
                                               :order order})))]
        (if (and product-id (empty? versions))
          (error/reject :cash-account-product/product-not-found
                        {:message "Product not found"
                         :bank-id bank-id
                         :product-id product-id})
          versions)))
    :cash-account-product/list-versions
    "Failed to list product versions")))
