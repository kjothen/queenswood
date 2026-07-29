(ns com.repldriven.queenswood.cash-account-query.store
  (:require
    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

;; must match bank-cash-account.store/store-name — same FDB store
(def ^:private store-name "cash-accounts")

(def transact fdb/transact)

(defn find-account-by-idempotency-key
  [txn bank-id idempotency-key]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/query-record-compound
              (fdb/open txn store-name)
              "CashAccount"
              [["bank_id" bank-id]
               ["idempotency_key" idempotency-key]]
              {:index "CashAccount_by_idempotency_key"})
             schema/pb->CashAccount))
   :cash-account/find-by-idempotency-key
   "Failed to find account by idempotency key"))

(defn find-account
  [txn bank-id account-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name)
                              bank-id
                              account-id)
             schema/pb->CashAccount))
   :cash-account/find
   "Failed to load account"))

(defn get-accounts
  ([txn bank-id]
   (get-accounts txn bank-id nil))
  ([txn bank-id opts]
   (let [{:keys [after before limit order]
          :or {limit 100 order :desc}}
         opts]
     (let-nom>
       [result (fdb/transact
                txn
                (fn [txn]
                  (fdb/scan-records
                   (fdb/open txn store-name)
                   {:prefix [bank-id]
                    :after after
                    :before before
                    :limit limit
                    :order order}))
                :cash-account/list
                "Failed to list accounts")
        {:keys [records before after]} result]
       {:accounts (mapv schema/pb->CashAccount records)
        :before before
        :after after}))))

(defn count-by-org
  [txn bank-id]
  (fdb/transact txn
                (fn [txn]
                  (fdb/count-records (fdb/open txn store-name)
                                     "CashAccount_count_by_bank"
                                     bank-id))
                :cash-account/count-by-org
                {:message "Failed to count accounts by org"
                 :bank-id bank-id}))

(defn count-by-org-product-account-type-currency
  [txn bank-id product-type account-type currency]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/count-records
      (fdb/open txn store-name)
      "CashAccount_count_by_bank_product_account_type_currency"
      [bank-id
       (schema/product-type->int product-type)
       (schema/account-type->int account-type)
       currency]))
   :cash-account/count-by-org-product-account-type-currency
   {:message
    "Failed to count accounts by org/product/account type/currency"
    :bank-id bank-id
    :product-type product-type
    :account-type account-type
    :currency currency}))

(defn find-account-by-product
  [txn bank-id product-id]
  (fdb/transact txn
                (fn [txn]
                  (some-> (fdb/query-record-compound
                           (fdb/open txn store-name)
                           "CashAccount"
                           [["bank_id" bank-id]
                            ["product_id" product-id]]
                           {:index "CashAccount_by_bank_product"})
                          schema/pb->CashAccount))
                :cash-account/find-by-product
                "Failed to find account by product"))

(defn find-accounts-by-party
  [txn bank-id party-id]
  (fdb/transact
   txn
   (fn [txn]
     (filterv #(= bank-id (:bank-id %))
              (mapv schema/pb->CashAccount
                    (fdb/query-records
                     (fdb/open txn store-name)
                     "CashAccount"
                     "party_id"
                     party-id
                     {:index "CashAccount_by_party"}))))
   :cash-account/find-by-party
   "Failed to find accounts by party"))

(defn get-account-by-bban
  [txn bban]
  (fdb/transact txn
                (fn [txn]
                  (some-> (fdb/query-record (fdb/open txn store-name)
                                            "CashAccount"
                                            "bban"
                                            bban
                                            {:index "CashAccount_by_bban"})
                          schema/pb->CashAccount))
                :cash-account/get-by-bban
                "Failed to get account by bban"))
