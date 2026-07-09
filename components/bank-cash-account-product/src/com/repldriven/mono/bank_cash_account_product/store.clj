(ns com.repldriven.mono.bank-cash-account-product.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

;; must match bank-cash-account-product-query.store store-names — same FDB
;; stores
(def ^:private store-name "cash-account-products")
(def ^:private templates-store-name "cash-account-product-templates")

(def transact fdb/transact)
(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-template
  [txn template]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn templates-store-name)
                      (schema/CashAccountProductTemplate->java template)))
   :cash-account-product/save-template
   "Failed to save template"))

(defn save-version
  [txn version]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record (fdb/open txn store-name)
                      (schema/CashAccountProduct->java version)))
   :cash-account-product/save-version
   "Failed to save product version"))
