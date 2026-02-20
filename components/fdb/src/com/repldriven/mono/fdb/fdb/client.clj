(ns com.repldriven.mono.fdb.fdb.client
  (:require
    [com.repldriven.mono.log.interface :as log])
  (:import
    (com.apple.foundationdb Database
                            FDB)))

(defn create
  "Create FDB client and database instance from configuration.

  Config map must contain:
  - :cluster-file-path - absolute path to FDB cluster file"
  [{:keys [cluster-file-path api-version]}]
  (try (log/info "Creating FDB client with cluster file:" cluster-file-path
                 "api-version:" api-version)
       (let [api-version (or api-version 730)
             _ (log/info "Selecting FDB API version:" api-version)
             fdb (FDB/selectAPIVersion api-version)
             _ (log/info "Opening FDB database...")
             db (.open fdb cluster-file-path)]
         (log/info "Opened FDB database with cluster file:" cluster-file-path
                   "db:" db)
         db)
       (catch Throwable e
         (log/error "Failed to create FDB database:" (.getMessage e))
         (.printStackTrace e)
         nil)))

(defn close
  "Close FDB database instance."
  [^Database db]
  (when (some? db) (log/info "Closing FDB database") (.close db)))
