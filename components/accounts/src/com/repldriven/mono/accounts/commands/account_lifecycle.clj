(ns com.repldriven.mono.accounts.commands.account-lifecycle
  (:require
    [com.repldriven.mono.accounts.commands.response :refer [->account]]

    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private account-returning [:account_id :customer_id :name :currency])

(defn- row->account-data
  [row]
  {"account_id" (str (:account_id row))
   "customer_id" (:customer_id row)
   "name" (:name row)
   "currency" (:currency row)})

(defn- ->account-or-reject
  "Returns a serialized account on success, a rejection if not found,
  or an anomaly on failure."
  [result schemas]
  (cond
   (error/anomaly? result)
   result

   (nil? result)
   {:status "REJECTED" :message "Account not found"}

   :else
   (->account schemas "ACCEPTED" (row->account-data result))))

(defn- ->account-or-fail
  "Returns a serialized account on success, or an anomaly on failure."
  [result schemas category data]
  (cond
   (error/anomaly? result)
   result

   (pos? (db/update-count result))
   (->account schemas "ACCEPTED" data)

   :else
   (error/fail category
               {:message "Failed to create account"
                :account_id (get data "account_id")})))

(defn- update-status
  [datasource schemas account_id status]
  (->account-or-reject (db/execute-one!
                        datasource
                        (sql/format {:update :account
                                     :set {:status [:inline status]
                                           :updated_at [:timezone "utc" [:now]]}
                                     :where [:= :account_id
                                             [:cast account_id :uuid]]
                                     :returning account-returning})
                        {:builder-fn db/as-unqualified-lower-maps})
                       schemas))

(defn open
  "Inserts a new account record with status open."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [customer_id name currency]} data
        account-id (utility/uuidv7)]
    (->account-or-fail (db/execute-one!
                        datasource
                        (sql/format {:insert-into :account
                                     :columns [:account_id :customer_id :name
                                               :currency :status]
                                     :values [[account-id customer_id name
                                               currency [:inline "open"]]]}))
                       schemas
                       :accounts/account-open
                       {"account_id" (str account-id)
                        "customer_id" customer_id
                        "name" name
                        "currency" currency})))

(defn close
  "Sets account status to closed."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id]} data]
    (update-status datasource schemas account_id "closed")))

(defn reopen
  "Sets account status back to open."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id]} data]
    (update-status datasource schemas account_id "open")))

(defn suspend
  "Sets account status to suspended."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id]} data]
    (update-status datasource schemas account_id "suspended")))

(defn unsuspend
  "Sets account status back to open from suspended."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id]} data]
    (update-status datasource schemas account_id "open")))

(defn archive
  "Sets account status to archived and records the deletion timestamp."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id]} data]
    (->account-or-reject (db/execute-one!
                          datasource
                          (sql/format
                           {:update :account
                            :set {:status [:inline "archived"]
                                  :updated_at [:timezone "utc" [:now]]
                                  :deleted_at [:timezone "utc" [:now]]}
                            :where [:= :account_id [:cast account_id :uuid]]
                            :returning account-returning})
                          {:builder-fn db/as-unqualified-lower-maps})
                         schemas)))
