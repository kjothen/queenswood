(ns com.repldriven.mono.processor.commands.account-lifecycle
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]))

(defn open
  "Open a new account by inserting a record into the database."
  [{:keys [datasource]} {:strs [account-id name currency]}]
  (let
    [result
     (db/execute-one!
      datasource
      ["INSERT INTO account (account_id, name, currency, status)
                  VALUES (?, ?, ?, 'open')"
       account-id
       name
       currency])]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-open
                            {:message "Failed to create account"
                             :account-id account-id}))))

(defn close
  "Close an existing account by updating its status to 'closed'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let
    [result
     (db/execute-one!
      datasource
      ["UPDATE account
                  SET status = 'closed',
                      updated_at = timezone('utc', now())
                  WHERE account_id = ?"
       account-id]
      {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-close
                            {:message "Account not found"
                             :account-id account-id}))))

(defn reopen
  "Reopen a closed account by updating its status to 'open'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let
    [result
     (db/execute-one!
      datasource
      ["UPDATE account
                  SET status = 'open',
                      updated_at = timezone('utc', now())
                  WHERE account_id = ?"
       account-id]
      {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-reopen
                            {:message "Account not found"
                             :account-id account-id}))))

(defn suspend
  "Suspend an account by updating its status to 'suspended'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let
    [result
     (db/execute-one!
      datasource
      ["UPDATE account
                  SET status = 'suspended',
                      updated_at = timezone('utc', now())
                  WHERE account_id = ?"
       account-id]
      {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-suspend
                            {:message "Account not found"
                             :account-id account-id}))))

(defn unsuspend
  "Unsuspend an account by updating its status to 'open'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let
    [result
     (db/execute-one!
      datasource
      ["UPDATE account
                  SET status = 'open',
                      updated_at = timezone('utc', now())
                  WHERE account_id = ?"
       account-id]
      {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-unsuspend
                            {:message "Account not found"
                             :account-id account-id}))))

(defn archive
  "Archive an account by updating its status to 'archived'."
  [{:keys [datasource]} {:strs [account-id]}]
  (let
    [result
     (db/execute-one!
      datasource
      ["UPDATE account
                  SET status = 'archived',
                      updated_at = timezone('utc', now()),
                      deleted_at = timezone('utc', now())
                  WHERE account_id = ?"
       account-id]
      {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:status :ok :account-id account-id}
          :else (error/fail :accounts/account-archive
                            {:message "Account not found"
                             :account-id account-id}))))

(defn get
  "Retrieve an account by account-id."
  [{:keys [datasource]} account-id]
  (db/execute-one!
   datasource
   ["SELECT account_id, name, status, currency, balance,
            created_at, updated_at, deleted_at
     FROM account
     WHERE account_id = ?"
    account-id]
   {:builder-fn db/as-unqualified-lower-maps}))
