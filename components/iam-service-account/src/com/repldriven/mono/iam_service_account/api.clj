(ns com.repldriven.mono.iam-service-account.api
  (:refer-clojure :exclude [delete get list name])
  (:require [clojure.string :as str]
            [com.repldriven.mono.iam-service-account.database :as database]
            [com.repldriven.mono.iam-service-account.spec :as spec]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(defn email
  [account-id project-id]
  (str account-id "@" project-id ".iam.serviceaccount"))

(defn name [name email] (str name "/serviceAccounts/" email))

(defn project-id [project-name] (second (str/split project-name #"/")))

(defn ->ServiceAccount
  [record]
  (update record :unique-id (fn [unique-id] (format "%021.0f" unique-id))))

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

(defn delete [db name])
(defn disable [db name])
(defn enable [db name])
(defn list [db project-name])
(defn get [db name])
(defn patch [db name body])
(defn undelete [db name])
