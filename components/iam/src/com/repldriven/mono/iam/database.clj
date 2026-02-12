(ns com.repldriven.mono.iam.database
  (:require
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.migrator.interface :as migrator]))

(defn migrate
  ([db-spec] (migrate db-spec "iam/init-changelog.sql"))
  ([db-spec resource-path]
   (error/try-nom :iam/migration-failed
                  "Failed to run database migrations"
                  (migrator/migrate db-spec resource-path))))
