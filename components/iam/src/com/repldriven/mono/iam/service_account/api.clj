(ns com.repldriven.mono.iam.service-account.api
  (:refer-clojure :exclude [delete get list name])
  (:require
    next.jdbc.date-time

    [com.repldriven.mono.iam.service-account.spec :as spec]

    [malli.generator :as mg]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]

    [clojure.string :as str]))

(defn email
  [account-id project-id]
  (str account-id "@" project-id ".iam.serviceaccount"))

(defn name [name email] (str name "/serviceAccounts/" email))

(defn project-id [project-name] (second (str/split project-name #"/")))

(defn ->ServiceAccount
  [record]
  (when record
    (-> record
        (dissoc :created-at :updated-at :deleted-at)
        (update :unique-id (fn [unique-id] (format "%021.0f" unique-id))))))

(defn create
  [db project-name body]
  (let [account-id      (clojure.core/get body "account-id")
        service-account (clojure.core/get body "service-account")
        display-name    (clojure.core/get service-account "display-name")
        description     (clojure.core/get service-account "description")
        project-id (project-id project-name)
        email (email account-id project-id)
        record {:name (name project-name email)
                :project-id project-id
                :email email
                :display-name display-name
                :description description
                :disabled false}]
    (->ServiceAccount (sql/insert! db
                                   :service-account
                                   record
                                   jdbc/unqualified-snake-kebab-opts))))

(defn delete
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account
SET deleted_at = timezone('utc', now())
WHERE name = ? AND deleted_at IS NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn undelete
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account
SET deleted_at = NULL, updated_at = timezone('utc', now())
WHERE name = ? AND deleted_at IS NOT NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn disable
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account
SET disabled = TRUE, updated_at = timezone('utc', now())
WHERE name = ? AND deleted_at IS NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn enable
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account
SET disabled = FALSE, updated_at = timezone('utc', now())
WHERE name = ? AND deleted_at IS NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn list
  [db project-name]
  (mapv
   ->ServiceAccount
   (sql/query
    db
    ["SELECT unique_id, name, project_id, email, display_name, description, disabled
FROM service_account
WHERE name LIKE ? AND deleted_at IS NULL"
     (str project-name "/serviceAccounts/%")]
    jdbc/unqualified-snake-kebab-opts)))

(defn get
  [db name]
  (->ServiceAccount
   (jdbc/execute-one!
    db
    ["SELECT unique_id, name, project_id, email, display_name, description, disabled
FROM service_account
WHERE name = ? AND deleted_at IS NULL"
     name]
    jdbc/unqualified-snake-kebab-opts)))

(defn patch [db name body])
