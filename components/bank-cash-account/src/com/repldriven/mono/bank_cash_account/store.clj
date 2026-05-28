(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "cash-accounts")

(def transact fdb/transact)

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

(defn save-account
  [txn account changelog]
  (fdb/transact
   txn
   (fn [txn]
     (let [store (fdb/open txn store-name)]
       (let-nom>
         [_ (fdb/save-record store (schema/CashAccount->java account))
          _ (fdb/write-changelog
             store
             store-name
             (:account-id account)
             (schema/CashAccountChangelog->pb
              (assoc changelog
                     :bank-id
                     (:bank-id account))))]
         nil)))
   :cash-account/save
   "Failed to save account"))

(defn allocate-payment-address
  [txn counter]
  (fdb/transact txn
                (fn [txn]
                  (format "%08d"
                          (fdb/allocate-counter (fdb/open txn store-name)
                                                "bank"
                                                "counters"
                                                counter)))
                :cash-account/allocate-payment-address
                "Failed to allocate payment address"))

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

(defn get-account-by-type
  [txn bank-id product-type]
  (fdb/transact txn
                (fn [txn]
                  (some-> (fdb/query-record-compound
                           (fdb/open txn store-name)
                           "CashAccount"
                           [["bank_id" bank-id]
                            ["product_type"
                             (schema/product-type->pb-enum product-type)]]
                           {:index "CashAccount_by_bank_product_type"})
                          schema/pb->CashAccount))
                :cash-account/get-by-type
                "Failed to get account by type"))

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
