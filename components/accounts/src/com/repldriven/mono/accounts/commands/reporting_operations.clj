(ns com.repldriven.mono.accounts.commands.reporting-operations
  (:require
    [com.repldriven.mono.accounts.commands.response :refer [->account-status]]

    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]))

(defn- ->account-status-or-reject
  [result schemas]
  (cond
   (error/anomaly? result)
   result

   (some? result)
   (->account-status schemas
                     "ACCEPTED"
                     {"account_id" (:account_id result)
                      "account_status" (:account_status result)})

   :else
   {:status "REJECTED" :message "Account not found"}))

(defn get-account-status
  "Returns the current status of an account."
  [config data]
  (let [{:keys [datasource schemas]} config
        {:strs [account_id]} data]
    (->account-status-or-reject
     (db/execute-one! datasource
                      (sql/format {:select [:account_id
                                            [:status :account_status]]
                                   :from :account
                                   :where [:= :account_id account_id]})
                      {:builder-fn db/as-unqualified-lower-maps})
     schemas)))
