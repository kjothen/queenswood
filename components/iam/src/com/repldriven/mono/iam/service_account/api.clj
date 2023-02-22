(ns com.repldriven.mono.iam.service-account.api
  (:refer-clojure :exclude [delete get list name])
  (:require [clojure.string :as str]
            [com.repldriven.mono.iam.service-account.spec :as spec]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [next.jdbc.sql :as sql]))

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
  (let [{:keys [account-id service-account]} body
        {:keys [display-name description]} service-account
        project-id (project-id project-name)
        email (email account-id project-id)
        record {:name (name project-name email)
                :project-id project-id
                :email email
                :display-name display-name
                :description description
                :disabled false}]
    (tap> record)
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
      ["UPDATE service_account SET deleted_at = timezone('utc', now()) WHERE name = ? AND deleted_at IS NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn undelete
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account SET deleted_at = NULL, updated_at = timezone('utc', now()) WHERE name = ? AND deleted_at IS NOT NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn disable
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account SET disabled = TRUE, updated_at = timezone('utc', now()) WHERE name = ? AND deleted_at IS NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn enable
  [db name]
  (let
    [res
     (jdbc/execute-one!
      db
      ["UPDATE service_account SET disabled = FALSE, updated_at = timezone('utc', now()) WHERE name = ? AND deleted_at IS NULL"
       name]
      jdbc/unqualified-snake-kebab-opts)]
    (>= (:next.jdbc/update-count res) 1)))

(defn list
  [db project-name]
  (mapv
   ->ServiceAccount
   (sql/query
    db
    ["select * from service_account where name like ? AND deleted_at IS NULL"
     (str project-name "%")]
    jdbc/unqualified-snake-kebab-opts)))

(defn get
  [db name]
  (->ServiceAccount
   (jdbc/execute-one!
    db
    ["SELECT * FROM service_account WHERE name = ? AND deleted_at IS NULL" name]
    jdbc/unqualified-snake-kebab-opts)))

(defn patch [db name body])

(comment
  (get
   {:database "test"
    :dbtype "postgres"
    :host "localhost"
    :password "test"
    :port 49224
    :user "test"}
   "projects/prj-12345/serviceAccounts/sa-123@prj-12345.iam.serviceaccount"))
