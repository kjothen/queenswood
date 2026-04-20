(ns com.repldriven.mono.bank-cash-account.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "cash-accounts")

(def transact fdb/transact)

(defn find-account
  "Loads a cash account by composite PK if it exists.
  Returns the account map, nil, or anomaly on I/O failure.
  For existence probes (e.g. watcher handlers)."
  [txn org-id account-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn store-name)
                              org-id
                              account-id)
             schema/pb->CashAccount))
   :cash-account/find
   "Failed to load account"))

(defn get-account
  "Loads a cash account by composite PK. Returns the account
  map or rejection anomaly if not found."
  [txn org-id account-id]
  (let-nom> [account (find-account txn org-id account-id)]
    (or account
        (error/reject :cash-account/not-found
                      {:message "Account not found"
                       :organization-id org-id
                       :account-id account-id}))))

(defn save-account
  "Saves account to the cash-accounts store and writes a
  changelog entry. Returns nil or anomaly."
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
                     :organization-id
                     (:organization-id account))))]
         nil)))
   :cash-account/save
   "Failed to save account"))

(defn allocate-payment-address
  "Allocates and formats the next payment-address number for
  the given counter."
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
  "Lists cash accounts for an organization. Returns
  {:accounts [maps] :before id|nil :after id|nil} or
  anomaly. opts supports :after, :before, :limit."
  ([txn org-id]
   (get-accounts txn org-id nil))
  ([txn org-id opts]
   (let-nom>
     [result (fdb/transact
              txn
              (fn [txn]
                (fdb/scan-records
                 (fdb/open txn store-name)
                 (merge {:prefix [org-id] :limit 100}
                        (select-keys opts [:after :before :limit]))))
              :cash-account/list
              "Failed to list accounts")
      {:keys [records before after]} result]
     {:accounts (mapv schema/pb->CashAccount records)
      :before before
      :after after})))

(defn count-party-accounts-by-type
  "Returns the count of accounts matching the given
  org-id, party-id and product-type. Uses the
  org_party_product_type_count count index."
  [txn org-id party-id product-type]
  (fdb/transact txn
                (fn [txn]
                  (fdb/count-records
                   (fdb/open txn store-name)
                   "CashAccount_count_by_org_party_product_type"
                   [org-id
                    party-id
                    (.getNumber
                     (schema/product-type->pb-enum product-type))]))
                :cash-account/count-party-accounts-by-type
                "Failed to count party accounts by type"))

(defn get-account-by-type
  "Returns the first account matching the given org-id and
  product-type, or nil. Pins the planner to the
  org_product_type_idx compound index."
  [txn org-id product-type]
  (fdb/transact txn
                (fn [txn]
                  (some-> (fdb/query-record-compound
                           (fdb/open txn store-name)
                           "CashAccount"
                           [["organization_id" org-id]
                            ["product_type"
                             (schema/product-type->pb-enum product-type)]]
                           {:index "CashAccount_by_org_product_type"})
                          schema/pb->CashAccount))
                :cash-account/get-by-type
                "Failed to get account by type"))

(defn get-account-by-bban
  "Returns the account matching the given bban, or nil.
  Pins the planner to the bban_idx secondary index."
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
