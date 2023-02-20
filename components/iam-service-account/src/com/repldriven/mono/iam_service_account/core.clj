(ns com.repldriven.mono.iam-service-account.core
  (:refer-clojure :exclude [get list])
  (:require [com.repldriven.mono.iam-service-account.database :as database]
            [com.repldriven.mono.iam-service-account.spec :as spec]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]))

(defn create
  [db-spec service-account]
  (let [account (merge (mg/generate spec/ServiceAccount) service-account)]
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
