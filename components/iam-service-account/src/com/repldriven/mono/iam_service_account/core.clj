(ns com.repldriven.mono.iam-service-account.core
  (:refer-clojure :exclude [get list])
  (:require [com.repldriven.mono.iam-service-account.database :as database]
            [com.repldriven.mono.iam-service-account.spec :as spec]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(defn create
  [db-spec
   {:keys [account-id project-id]
    {:keys [display-name description]} :service-account}]
  (let [email (str account-id "@" project-id ".iam.serviceaccount")
        sa-name (str "projects/" project-id "/serviceAccounts/" email)
        account {:name sa-name
                 :project-id project-id
                 :email email
                 :display-name display-name
                 :description description
                 :enabled true}]
    (tap> account)
    (sql/insert! db-spec
                 :service-account
                 account
                 jdbc/unqualified-snake-kebab-opts)))

(defn delete [db service-account])

(defn disable [db service-account])

(defn enable [db service-account])

(defn list [db-spec])

(defn get [db id])

(defn patch [db service-account])

(defn undelete [db service-account])
