(ns com.repldriven.mono.iam-service-account.core
  (:require [com.repldriven.mono.iam-service-account.database :as database]))

(defn create [db service-account])

(defn delete [db service-account])

(defn disable [db service-account])

(defn enable [db service-account])

(defn list [db])

(defn get [db id])

(defn patch [db service-account])

(defn undelete [db service-account])
