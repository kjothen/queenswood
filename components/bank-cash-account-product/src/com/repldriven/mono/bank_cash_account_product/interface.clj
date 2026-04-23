(ns com.repldriven.mono.bank-cash-account-product.interface
  (:require
    [com.repldriven.mono.bank-cash-account-product.core :as core]))

(defn new-product
  [txn org-id data]
  (core/new-product txn org-id data))

(defn open-draft
  [txn org-id product-id data]
  (core/open-draft txn org-id product-id data))

(defn update-draft
  [txn org-id product-id version-id data]
  (core/update-draft txn org-id product-id version-id data))

(defn discard-draft
  [txn org-id product-id version-id]
  (core/discard-draft txn org-id product-id version-id))

(defn publish
  [txn org-id product-id version-id]
  (core/publish txn org-id product-id version-id))

(defn get-version
  [txn org-id product-id version-id]
  (core/get-version txn org-id product-id version-id))

(defn get-product
  [txn org-id product-id]
  (core/get-product txn org-id product-id))

(defn get-products
  ([txn org-id] (core/get-products txn org-id))
  ([txn org-id opts] (core/get-products txn org-id opts)))
