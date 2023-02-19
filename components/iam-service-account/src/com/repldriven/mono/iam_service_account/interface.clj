(ns com.repldriven.mono.iam-service-account.interface
  (:refer-clojure :exclude [get list])
  (:require [com.repldriven.mono.iam-service-account.core :as core]
            [com.repldriven.mono.iam-service-account.database :as database]
            [com.repldriven.mono.iam-service-account.spec :as spec]))

;;;; Data Schema

(def ServiceAccount spec/ServiceAccount)

;;;; Database

(defn migrate
  ([db-spec] (database/migrate db-spec))
  ([db-spec version] (database/migrate db-spec version)))

;;;; ServiceAccount Operations

(defn create [db-spec service-account] (core/create db-spec service-account))

(defn delete [db service-account] (core/delete db service-account))

(defn disable [db service-account] (core/disable db service-account))

(defn enable [db service-account] (core/enable db service-account))

(defn list [db] (core/list db))

(defn get [db id] (core/get db id))

(defn patch [db service-account] (core/patch db service-account))

(defn undelete [db service-account] (core/undelete db service-account))
