(ns com.repldriven.mono.processor.commands.account
  (:refer-clojure :exclude [get])
  (:require
   [com.repldriven.mono.db.interface :as db]
   [com.repldriven.mono.error.interface :as error]))

(defn open
  "Open a new account by inserting a record into the database."
  [{:keys [datasource]} {:keys [account-id name currency]}]
  (let [result (db/execute-one! datasource
                                ["INSERT INTO account (account_id, name, currency, status)
                                  VALUES (?, ?, ?, 'open')"
                                 account-id
                                 name
                                 currency])]
    (if (error/anomaly? result)
      result
      {:status :ok :account-id account-id})))

(defn close
  "Close an existing account by updating its status to 'closed'."
  [{:keys [datasource]} {:keys [account-id]}]
  (let [result (db/execute-one! datasource
                                ["UPDATE account
                                  SET status = 'closed',
                                      updated_at = timezone('utc', now())
                                  WHERE account_id = ?"
                                 account-id]
                                {:return-keys false})]
    (cond
      (error/anomaly? result) result
      (pos? (db/update-count result)) {:status :ok :account-id account-id}
      :else (error/fail :accounts/account-not-found
                        (str "Account not found: " account-id)))))

(defn get
  "Retrieve an account by account-id."
  [{:keys [datasource]} account-id]
  (db/execute-one! datasource
                   ["SELECT account_id, name, status, currency, balance, created_at, updated_at
                     FROM account
                     WHERE account_id = ?"
                    account-id]
                   {:builder-fn db/as-unqualified-lower-maps}))
