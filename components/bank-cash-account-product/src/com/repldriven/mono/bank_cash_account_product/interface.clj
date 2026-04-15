(ns com.repldriven.mono.bank-cash-account-product.interface
  (:require
    [com.repldriven.mono.bank-cash-account-product.core :as core]))

(defn new-product
  [txn org-id data]
  (core/new-product txn org-id data))

(defn new-version
  [txn org-id product-id data]
  (core/new-version txn org-id product-id data))

(defn get-version
  [txn org-id product-id version-id]
  (core/get-version txn org-id product-id version-id))

(defn get-versions
  ([txn org-id] (core/get-versions txn org-id))
  ([txn org-id product-id] (core/get-versions txn org-id product-id)))

(defn get-published
  [txn org-id product-id]
  (core/get-published txn org-id product-id))

(defn publish
  [txn org-id product-id version-id]
  (core/publish txn org-id product-id version-id))
