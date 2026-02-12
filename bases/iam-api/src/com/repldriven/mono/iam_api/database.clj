(ns com.repldriven.mono.iam-api.database
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.iam.interface :as iam]
    [com.repldriven.mono.system.interface :as system]))

(defn migrate
  "Run database migrations for the IAM API.
  Takes a system and migrates the database using the datasource from the system.
  Returns the system on success or an anomaly on failure."
  [sys]
  (error/let-nom> [datasource (system/instance sys [:db :datasource])
                   _ (iam/migrate (db/get-datasource datasource))]
    sys))
