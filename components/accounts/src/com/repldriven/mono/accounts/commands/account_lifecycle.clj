(ns com.repldriven.mono.accounts.commands.account-lifecycle
  (:require
    [com.repldriven.mono.accounts.commands.response :refer [->account]]

    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]))

(defn- ->account-or-reject
  "Returns a serialized account on success, a rejection if not found,
  or an anomaly on failure."
  [result schemas data]
  (cond
   (error/anomaly? result)
   result

   (zero? (db/update-count result))
   {:status "REJECTED" :message "Account not found"}

   :else
   (->account schemas "ACCEPTED" data)))

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
                :account_id (:account_id data)})))

(defn- update-status
  [datasource schemas account_id status]
  (->account-or-reject (db/execute-one!
                        datasource
                        (sql/format {:update :account
                                     :set {:status [:inline status]
                                           :updated_at [:timezone "utc" [:now]]}
                                     :where [:= :account_id account_id]})
                        {:return-keys false})
                       schemas
                       {"account_id" account_id}))

(defn open
  "Inserts a new account record with status open."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id name currency]} data]
    (->account-or-fail
     (db/execute-one! datasource
                      (sql/format
                       {:insert-into :account
                        :columns [:account_id :name :currency :status]
                        :values [[account_id name currency [:inline "open"]]]}))
     schemas
     :accounts/account-open
     {"account_id" account_id})))

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
    (->account-or-reject
     (db/execute-one! datasource
                      (sql/format {:update :account
                                   :set {:status [:inline "archived"]
                                         :updated_at [:timezone "utc" [:now]]
                                         :deleted_at [:timezone "utc" [:now]]}
                                   :where [:= :account_id account_id]})
                      {:return-keys false})
     schemas
     {"account_id" account_id})))
