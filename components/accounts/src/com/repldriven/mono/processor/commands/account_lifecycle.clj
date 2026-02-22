(ns com.repldriven.mono.processor.commands.account-lifecycle
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]))

(defn open
  "Open a new account by inserting a record into the database."
  [{:keys [datasource]} {:strs [account-id name currency]}]
  (let [result (db/execute-one! datasource
                                (sql/format {:insert-into :account
                                             :columns [:account_id :name
                                                       :currency :status]
                                             :values [[account-id name currency
                                                       [:inline "open"]]]}))]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-open
                            {:message "Failed to create account"
                             :account-id account-id}))))

(defn close
  "Close an existing account by updating its status to 'closed'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "closed"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account-id]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-close
                            {:message "Account not found"
                             :account-id account-id}))))

(defn reopen
  "Reopen a closed account by updating its status to 'open'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "open"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account-id]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-reopen
                            {:message "Account not found"
                             :account-id account-id}))))

(defn suspend
  "Suspend an account by updating its status to 'suspended'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "suspended"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account-id]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-suspend
                            {:message "Account not found"
                             :account-id account-id}))))

(defn unsuspend
  "Unsuspend an account by updating its status to 'open'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "open"]
                                        :updated_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account-id]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-unsuspend
                            {:message "Account not found"
                             :account-id account-id}))))

(defn archive
  "Archive an account by updating its status to 'archived'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let [result (db/execute-one! datasource
                                (sql/format
                                 {:update :account
                                  :set {:status [:inline "archived"]
                                        :updated_at [:timezone "utc" [:now]]
                                        :deleted_at [:timezone "utc" [:now]]}
                                  :where [:= :account_id account-id]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-archive
                            {:message "Account not found"
                             :account-id account-id}))))

(defn get
  "Retrieve an account by account-id."
  [{:keys [datasource]} account-id]
  (db/execute-one! datasource
                   (sql/format {:select [:account_id :name :status :currency
                                         :balance :created_at :updated_at
                                         :deleted_at]
                                :from :account
                                :where [:= :account_id account-id]})
                   {:builder-fn db/as-unqualified-lower-maps}))
