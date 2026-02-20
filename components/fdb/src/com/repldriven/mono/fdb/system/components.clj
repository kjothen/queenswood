(ns com.repldriven.mono.fdb.system.components
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (com.apple.foundationdb FDB)))

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
;; database
;; ---

(def database
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
;; client
;; ---

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance {:db (:database config)}))
   :system/stop (fn [_] nil)
   :system/config {:database system/required-component}})
