(ns com.repldriven.mono.iam-service-account.interface
  (:refer-clojure :exclude [delete get list name])
  (:require [com.repldriven.mono.iam-service-account.api :as api]
            [com.repldriven.mono.iam-service-account.database :as database]
            [com.repldriven.mono.iam-service-account.spec :as spec]))

;;;; Data Schema

(def ServiceAccount spec/ServiceAccount)
(def CreateBody spec/CreateBody)
(def PatchBody spec/CreateBody)

;;;; Database

(defn migrate
  ([db-spec] (database/migrate db-spec))
  ([db-spec version] (database/migrate db-spec version)))

;;;; ServiceAccount API

(defn create [db project-name body] (api/create db project-name body))
(defn delete [db name] (api/delete db name))
(defn disable [db name] (api/disable db name))
(defn enable [db name] (api/enable db name))
(defn list [db project-name] (api/list db project-name))
(defn get [db name] (api/get db name))
(defn patch [db name body] (api/patch db name))
(defn undelete [db name] (api/undelete db name))
