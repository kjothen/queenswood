(ns com.repldriven.mono.iam.interface
  (:refer-clojure :exclude [delete get list name])
  (:require
    [com.repldriven.mono.iam.service-account :as service-account]))

(defn create-service-account
  [db project-name account-id display-name description]
  (service-account/create db project-name account-id display-name description))

(defn delete-service-account [db name] (service-account/delete db name))

(defn disable-service-account [db name] (service-account/disable db name))

(defn enable-service-account [db name] (service-account/enable db name))

(defn list-service-account
  [db project-name]
  (service-account/list db project-name))

(defn get-service-account [db name] (service-account/get db name))

(defn patch-service-account
  [db name display-name description]
  (service-account/patch db name display-name description))

(defn undelete-service-account [db name] (service-account/undelete db name))

