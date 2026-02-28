(ns com.repldriven.mono.fdb.system.components
  (:require
    [com.repldriven.mono.fdb.keyspace :as keyspace]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system])
  (:import
    (com.apple.foundationdb FDB)
    (com.apple.foundationdb.record RecordMetaData)
    (com.apple.foundationdb.record.metadata Index Key$Expressions)
    (com.apple.foundationdb.record.provider.foundationdb APIVersion
                                                         FDBDatabaseFactory
                                                         FDBMetaDataStore
                                                         FDBRecordStore)
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
   :system/config {:container system/required-component}
   :system/config-schema [:map [:container some?]]
   :system/instance-schema string?})

;; ---
;; db
;; ---

(def db
  {:system/start (fn [{:system/keys [config instance]}]
                   (let [{:keys [cluster-file-path api-version]} config
                         api-version (or api-version 710)]
                     (log/info "FDB database start called, instance:" instance
                               "config:" config)
                     (or instance
                         (error/try-nom
                          :fdb/create-db
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
                   :api-version 710}
   :system/config-schema [:map [:cluster-file-path string?]]
   :system/instance-schema some?})

;; ---
;; record-db
;; ---

(def record-db
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (error/try-nom
                        :fdb/create-record-db
                        {:message "Failed to create FDB Record Layer database"}
                        (let [{:keys [cluster-file-path]} config]
                          (log/info "Opening FDB Record Layer database")
                          (.getDatabase
                           (doto (FDBDatabaseFactory/instance)
                             (.setAPIVersion APIVersion/API_VERSION_7_1)
                             (.setScheduledExecutor
                              (Executors/newSingleThreadScheduledExecutor)))
                           cluster-file-path)))))
   :system/stop (fn [{:system/keys [instance]}]
                  (when (some? instance)
                    (log/info "Closing FDB Record Layer database")
                    (.close instance)))
   :system/config {:cluster-file-path system/required-component}
   :system/config-schema [:map [:cluster-file-path string?]]
   :system/instance-schema some?})

;; ---
;; store
;; ---

(defn- resolve-descriptor
  [class-name]
  (let [clazz (Class/forName class-name)
        method (.getMethod clazz "getDescriptor" (into-array Class []))]
    (.invoke method nil (into-array Object []))))

(def store
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [descriptor record-types]} config
               file-desc (resolve-descriptor descriptor)
               registry (reduce-kv
                         (fn [reg store-name record-type-cfg]
                           (let [{:keys [record-type indexes]} record-type-cfg
                                 b (-> (RecordMetaData/newBuilder)
                                       (.setRecords file-desc))]
                             (doseq [{:keys [name field]} indexes]
                               (.addIndex
                                b
                                record-type
                                (Index. name (Key$Expressions/field field))))
                             (assoc reg store-name (.build b))))
                         {}
                         record-types)]
           (fn [ctx store-name]
             (let [meta (get registry store-name)]
               (when-not meta
                 (throw (ex-info "Unknown record store" {:store store-name})))
               (-> (FDBRecordStore/newBuilder)
                   (.setMetaDataProvider meta)
                   (.setContext ctx)
                   (.setKeySpacePath (keyspace/records-path store-name))
                   .createOrOpen))))))
   :system/config {:descriptor system/required-component
                   :record-types system/required-component}
   :system/config-schema [:map [:descriptor string?] [:record-types map?]]
   :system/instance-schema fn?})

;; ---
;; meta-store
;; ---

(defn- build-meta-data
  [descriptor record-types]
  (let [file-desc (resolve-descriptor descriptor)
        b (-> (RecordMetaData/newBuilder)
              (.setRecords file-desc))]
    (doseq [[_store-name {:keys [record-type indexes]}] record-types]
      (doseq [{:keys [name field]} indexes]
        (.addIndex b record-type (Index. name (Key$Expressions/field field)))))
    (.build b)))

(def meta-store
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [record-db meta-path descriptor record-types]} config
               path (keyspace/meta-path meta-path)
               file-desc (resolve-descriptor descriptor)
               meta-data (build-meta-data descriptor record-types)]
           (log/info "FDB meta-store saving metadata to:" meta-path)
           (.run record-db
                 (reify
                  java.util.function.Function
                    (apply [_ ctx]
                      (let [ms (FDBMetaDataStore. ctx path)]
                        (.saveRecordMetaData ms meta-data))
                      nil)))
           (fn [ctx store-name]
             (let [ms (doto (FDBMetaDataStore. ctx path)
                        (.setLocalFileDescriptor file-desc))]
               (-> (FDBRecordStore/newBuilder)
                   (.setMetaDataStore ms)
                   (.setContext ctx)
                   (.setKeySpacePath (keyspace/records-path store-name))
                   .createOrOpen))))))
   :system/config {:record-db system/required-component
                   :meta-path system/required-component
                   :descriptor system/required-component
                   :record-types system/required-component}
   :system/config-schema [:map [:record-db some?] [:meta-path string?]
                          [:descriptor string?] [:record-types map?]]
   :system/instance-schema fn?})
