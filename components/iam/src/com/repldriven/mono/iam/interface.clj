(ns com.repldriven.mono.iam.interface
  (:refer-clojure :exclude [delete get list name])
  (:require
    [com.repldriven.mono.iam.service-account :as service-account]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private schemas
  (-> "schemas/iam/iam.edn"
      io/resource
      slurp
      edn/read-string))

;;;; Shared Schema
(def ProjectId (:ProjectId schemas))

;;;; ServiceAcccounts
;;;;

;;; Schema
(def EmailAddressOrUniqueId (:EmailAddressOrUniqueId schemas))
(def ServiceAccount (:ServiceAccount schemas))
(def ServiceAccountCreateBody (:ServiceAccountCreateBody schemas))
(def ServiceAccountPatchBody (:ServiceAccountCreateBody schemas))

;;; Methods
(defn create-service-account
  [db project-name body]
  (service-account/create db project-name body))
(defn delete-service-account [db name] (service-account/delete db name))
(defn disable-service-account [db name] (service-account/disable db name))
(defn enable-service-account [db name] (service-account/enable db name))
(defn list-service-account
  [db project-name]
  (service-account/list db project-name))
(defn get-service-account [db name] (service-account/get db name))
(defn patch-service-account [db name body] (service-account/patch db name body))
(defn undelete-service-account [db name] (service-account/undelete db name))

;;;; Other IAM things...
;;;;
