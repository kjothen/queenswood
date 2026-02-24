(ns com.repldriven.mono.fdb.system.components
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (com.apple.foundationdb FDB)
    (com.apple.foundationdb.record RecordMetaData)
    (com.apple.foundationdb.record.metadata Index Key$Expressions)
    (com.apple.foundationdb.record.provider.foundationdb APIVersion
                                                         FDBDatabaseFactory)
    (java.util.concurrent Executors)))

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
                         api-version (or api-version 710)]
                     (log/info "FDB database start called, instance:"
                               instance "config:" config)
                     (or instance
                         (error/try-nom
                          :fdb/create-db
                          {:message "Failed to create FDB database"
                           :cluster-file-path cluster-file-path}
                          (let [fdb (FDB/selectAPIVersion api-version)
                                db (.open fdb cluster-file-path)]
                            (log/info
                             "Opened FDB database with cluster file:"
                             cluster-file-path)
                            db)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component
                   :api-version 710}})

;; ---
;; record-db
;; ---

(def record-db
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (error/try-nom
                        :fdb/create-record-db
                        {:message
                         "Failed to create FDB Record Layer database"}
                        (let [{:keys [cluster-file-path]} config]
                          (log/info
                           "Opening FDB Record Layer database")
                          (.getDatabase
                           (doto (FDBDatabaseFactory/instance)
                             (.setAPIVersion
                              APIVersion/API_VERSION_7_1)
                             (.setScheduledExecutor
                              (Executors/newSingleThreadScheduledExecutor)))
                           cluster-file-path)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB Record Layer database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component}})

;; ---
;; store
;; ---

(defn- resolve-descriptor
  [class-name]
  (let [clazz (Class/forName class-name)
        method (.getMethod clazz
                           "getDescriptor"
                           (into-array Class []))]
    (.invoke method nil (into-array Object []))))

(def store
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [descriptor record-types]} config
               file-desc (resolve-descriptor descriptor)]
           (reduce-kv
            (fn [registry store-name record-type-cfg]
              (let [{:keys [record-type indexes]}
                    record-type-cfg
                    b (-> (RecordMetaData/newBuilder)
                          (.setRecords file-desc))]
                (doseq [{:keys [name field]} indexes]
                  (.addIndex b
                             record-type
                             (Index. name
                                     (Key$Expressions/field
                                      field))))
                (assoc registry store-name (.build b))))
            {}
            record-types))))
   :system/config {:descriptor system/required-component
                   :record-types system/required-component}})
