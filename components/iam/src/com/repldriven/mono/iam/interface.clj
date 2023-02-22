(ns com.repldriven.mono.iam.interface
  (:refer-clojure :exclude [delete get list name])
  (:require
   [com.repldriven.mono.iam.service-account.api :as service-account-api]
   [com.repldriven.mono.iam.service-account.spec :as service-account-spec]
   [com.repldriven.mono.iam.database :as database]
   [com.repldriven.mono.iam.spec :as spec]))

;;;; Shared Schema
(def ProjectId spec/ProjectId)

;;;; Database
(defn migrate
  ([db-spec] (database/migrate db-spec))
  ([db-spec version] (database/migrate db-spec version)))

;;;; ServiceAcccounts
;;;;

;;; Schema
(def EmailAddressOrUniqueId service-account-spec/EmailAddressOrUniqueId)
(def ServiceAccount service-account-spec/ServiceAccount)
(def ServiceAccountCreateBody service-account-spec/CreateBody)
(def ServiceAccountPatchBody service-account-spec/CreateBody)

;;; Methods
(defn create-service-account
  [db project-name body]
  (service-account-api/create db project-name body))
(defn delete-service-account [db name] (service-account-api/delete db name))
(defn disable-service-account [db name] (service-account-api/disable db name))
(defn enable-service-account [db name] (service-account-api/enable db name))
(defn list-service-account
  [db project-name]
  (service-account-api/list db project-name))
(defn get-service-account [db name] (service-account-api/get db name))
(defn patch-service-account [db name body] (service-account-api/patch db name))
(defn undelete-service-account [db name] (service-account-api/undelete db name))


;;;; Other IAM things...
;;;;
