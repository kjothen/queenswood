(ns com.repldriven.mono.iam.database
  (:require
    [com.repldriven.mono.migrator.interface :as migrator]))

(defn migrate
  ([db-spec] (migrate db-spec "schemas/iam/init-changelog.sql"))
  ([db-spec resource-path] (migrator/migrate db-spec resource-path)))
