(ns com.repldriven.mono.processor.commands.reporting-operations
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]))

(defn get-account-status
  "Return the current status of an account."
  [{:keys [datasource]} {:strs [account-id]}]
  (let [row (db/execute-one!
             datasource
             ["SELECT account_id, status FROM account WHERE account_id = ?"
              account-id]
             {:builder-fn db/as-unqualified-lower-maps})]
    (cond (error/anomaly? row) row
          (some? row) {:status :ok
                       :account-id (:account_id row)
                       :account-status (:status row)}
          :else (error/fail :accounts/get-account-status
                            {:message "Account not found"
                             :account-id account-id}))))
