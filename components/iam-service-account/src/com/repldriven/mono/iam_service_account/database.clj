(ns com.repldriven.mono.iam-service-account.database
  (:require [com.repldriven.mono.migrator.interface :as migrator]))

(defn migrate
  ([db-spec] (migrate db-spec "iam-service-account/init-changelog.sql"))
  ([db-spec resource-path] (migrator/migrate db-spec resource-path)))
