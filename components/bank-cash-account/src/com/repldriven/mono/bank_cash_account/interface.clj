(ns com.repldriven.mono.bank-cash-account.interface
  (:require
    com.repldriven.mono.bank-cash-account.system

    [com.repldriven.mono.bank-cash-account.core :as core]))

(defn new-account
  "Opens a cash account with balances. Returns account map or
  anomaly."
  [txn data]
  (core/new-account txn data))

(defn get-account
  "Loads a single cash account. Returns the account map,
  nil, or anomaly. opts supports :embed-balances,
  :embed-transactions."
  ([txn org-id account-id]
   (core/get-account txn org-id account-id))
  ([txn org-id account-id opts]
   (core/get-account txn org-id account-id opts)))

(defn get-accounts
  "Lists cash accounts for an organization. Returns
  {:accounts [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit,
  :embed-balances, :embed-transactions."
  ([txn org-id]
   (core/get-accounts txn org-id))
  ([txn org-id opts]
   (core/get-accounts txn org-id opts)))

(defn get-account-by-type
  "Returns the first account matching the given org-id and
  product-type, or nil. Uses compound secondary index;
  caller should expect at most one result."
  [txn org-id product-type]
  (core/get-account-by-type txn org-id product-type))

(defn get-account-by-bban
  "Returns account matching the given BBAN.
  Uses secondary index."
  [txn bban]
  (core/get-account-by-bban txn bban))
