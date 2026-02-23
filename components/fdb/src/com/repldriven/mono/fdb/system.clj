(ns com.repldriven.mono.fdb.system
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (com.apple.foundationdb FDB)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabaseFactory)))

;; ---
;; cluster-file-path
;; ---

(def cluster-file-path
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [path (.getClusterFilePath (:container config))]
                         (log/info "FDB cluster file path:" path)
                         path)))
   :system/config {:container system/required-component}})

;; ---
;; db
;; ---

(def db
  {:system/start (fn [{:system/keys [config instance]}]
                   (let [{:keys [cluster-file-path api-version]} config
                         api-version (or api-version 730)]
                     (log/info "FDB database start called, instance:" instance
                               "config:" config)
                     (or instance
                         (error/try-nom
                          :fdb/create
                          {:message "Failed to create FDB database"
                           :cluster-file-path cluster-file-path}
                          (let [fdb (FDB/selectAPIVersion api-version)
                                db (.open fdb cluster-file-path)]
                            (log/info "Opened FDB database with cluster file:"
                                      cluster-file-path)
                            db)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component
                   :api-version 730}})

;; ---
;; record-db
;; ---

(def record-db
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [cluster-file-path]} config]
                         (log/info "Opening FDB Record Layer database")
                         (.getDatabase (FDBDatabaseFactory/instance)
                                       cluster-file-path))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB Record Layer database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component}})

(system/defcomponents
 :fdb
 {:cluster-file-path cluster-file-path :db db :record-db record-db})
