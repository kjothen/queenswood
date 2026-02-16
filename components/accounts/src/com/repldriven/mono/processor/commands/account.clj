(ns com.repldriven.mono.processor.commands.account
  (:refer-clojure :exclude [get])
  (:require
   [com.repldriven.mono.error.interface :as error]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]))

(defn open
  "Open a new account by inserting a record into the database.

  Config structure:
  {:datasource ...}

  Command data structure:
  {:account-id \"...\", :name \"...\", :currency \"USD\"}

  Returns success response or anomaly."
  [{:keys [datasource]} {:keys [account-id name currency]}]
  (error/try-nom :accounts/open-account-failed
                 "Failed to open account"
                 (jdbc/execute-one! datasource
                                    ["INSERT INTO account (account_id, name, currency, status)
                                      VALUES (?, ?, ?, 'open')"
                                     account-id
                                     name
                                     (or currency "USD")])
                 {:status :ok :account-id account-id}))

(defn close
  "Close an existing account by updating its status to 'closed'.

  Config structure:
  {:datasource ...}

  Command data structure:
  {:account-id \"...\"}

  Returns success response or anomaly."
  [{:keys [datasource]} {:keys [account-id]}]
  (error/try-nom :accounts/close-account-failed
                 "Failed to close account"
                 (let [result (jdbc/execute-one! datasource
                                                 ["UPDATE account
                                                   SET status = 'closed',
                                                       updated_at = timezone('utc', now())
                                                   WHERE account_id = ?"
                                                  account-id]
                                                 {:return-keys false})]
                   (if (pos? (:next.jdbc/update-count result))
                     {:status :ok :account-id account-id}
                     (error/fail :accounts/account-not-found
                                 (str "Account not found: " account-id))))))

(defn get
  "Retrieve an account by account-id.

  Config structure:
  {:datasource ...}

  Returns account map or nil if not found."
  [{:keys [datasource]} account-id]
  (error/try-nom :accounts/get-account-failed
                 "Failed to retrieve account"
                 (jdbc/execute-one! datasource
                                    ["SELECT account_id, name, status, currency, balance, created_at, updated_at
                                      FROM account
                                      WHERE account_id = ?"
                                     account-id]
                                    {:builder-fn rs/as-unqualified-lower-maps})))
