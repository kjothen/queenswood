(ns com.repldriven.mono.accounts.commands.reporting-operations
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]))

(defn get-account-status
  [{:keys [datasource]} {:strs [account_id]}]
  (let [row (db/execute-one! datasource
                             (sql/format {:select [:account_id :status]
                                          :from :account
                                          :where [:= :account_id account_id]})
                             {:builder-fn db/as-unqualified-lower-maps})]
    (cond
     (error/anomaly? row)
     row

     (some? row)
     {:status :ok
      :account_id (:account_id row)
      :account-status (:status row)}

     :else
     (error/fail :accounts/get-account-status
                 {:message "Account not found"
                  :account_id account_id}))))
