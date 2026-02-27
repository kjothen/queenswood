(ns com.repldriven.mono.accounts.commands.account-lifecycle
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]))

(defn open
  [{:keys [datasource]} {:strs [account_id name currency]}]
  (let [result (db/execute-one! datasource
                                (sql/format {:insert-into :account
                                             :columns [:account_id :name
                                                       :currency :status]
                                             :values [[account_id name currency
                                                       [:inline "open"]]]}))]
    (cond
     (error/anomaly? result)
     result

     (pos? (db/update-count result))
     {:status :ok :account_id account_id}

     :else
     (error/fail :accounts/account-open
                 {:message "Failed to create account"
                  :account_id account_id}))))

(defn close
  [{:keys [datasource]} {:strs [account_id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "closed"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account_id]})
                                {:return-keys false})]
    (cond
     (error/anomaly? result)
     result

     (pos? (db/update-count result))
     {:status :ok :account_id account_id}

     :else
     (error/fail :accounts/account-close
                 {:message "Account not found"
                  :account_id account_id}))))

(defn reopen
  [{:keys [datasource]} {:strs [account_id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "open"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account_id]})
                                {:return-keys false})]
    (cond
     (error/anomaly? result)
     result

     (pos? (db/update-count result))
     {:status :ok :account_id account_id}

     :else
     (error/fail :accounts/account-reopen
                 {:message "Account not found"
                  :account_id account_id}))))

(defn suspend
  [{:keys [datasource]} {:strs [account_id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "suspended"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account_id]})
                                {:return-keys false})]
    (cond
     (error/anomaly? result)
     result

     (pos? (db/update-count result))
     {:status :ok :account_id account_id}

     :else
     (error/fail :accounts/account-suspend
                 {:message "Account not found"
                  :account_id account_id}))))

(defn unsuspend
  [{:keys [datasource]} {:strs [account_id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "open"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account_id]})
                                {:return-keys false})]
    (cond
     (error/anomaly? result)
     result

     (pos? (db/update-count result))
     {:status :ok :account_id account_id}

     :else
     (error/fail :accounts/account-unsuspend
                 {:message "Account not found"
                  :account_id account_id}))))

(defn archive
  [{:keys [datasource]} {:strs [account_id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "archived"]
                                        :updated_at [:timezone "utc" [:now]]
                                        :deleted_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account_id]})
                                {:return-keys false})]
    (cond
     (error/anomaly? result)
     result

     (pos? (db/update-count result))
     {:status :ok :account_id account_id}

     :else
     (error/fail :accounts/account-archive
                 {:message "Account not found"
                  :account_id account_id}))))
