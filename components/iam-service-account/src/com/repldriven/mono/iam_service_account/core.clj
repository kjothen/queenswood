(ns com.repldriven.mono.iam-service-account.core
  (:refer-clojure :exclude [get list])
  (:require [com.repldriven.mono.iam-service-account.database :as database]
            [com.repldriven.mono.iam-service-account.spec :as spec]
            [malli.generator :as mg]
            [next.jdbc.sql :as sql]))

(defn create
  [db-spec service-account]
  (sql/insert! db-spec
               :service_accounts
               (merge (mg/generate spec/ServiceAccount) service-account)))

(defn delete [db service-account])

(defn disable [db service-account])

(defn enable [db service-account])

(defn list [db])

(defn get [db id])

(defn patch [db service-account])

(defn undelete [db service-account])
