(ns com.repldriven.mono.fdb.core
  (:refer-clojure :exclude [get set])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.outbox :as outbox]
    [com.repldriven.mono.fdb.store :as store])
  (:import
    (com.apple.foundationdb Database)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase)
    (com.google.protobuf MessageLite)))

(defn set
  "Set a string key-value pair in the FDB database."
  [^Database db ^String key ^String value]
  (error/try-nom
   :fdb/set
   {:message "Failed to set value" :key key}
   (.run db
         (reify
          java.util.function.Function
            (apply [_ tr] (.set tr (.getBytes key) (.getBytes value)) nil)))))

(defn get
  "Get a string value by key from the FDB database.
  Returns nil if the key does not exist."
  [^Database db ^String key]
  (error/try-nom :fdb/get
                 {:message "Failed to get value" :key key}
                 (.run db
                       (reify
                        java.util.function.Function
                          (apply [_ tr]
                            (some-> (.get tr (.getBytes key))
                                    .join
                                    (String.)))))))

(defn set-bytes
  "Set a byte array value for a string key in FDB."
  [^Database db ^String key ^bytes value]
  (error/try-nom :fdb/set-bytes
                 {:message "Failed to set bytes" :key key}
                 (.run db
                       (reify
                        java.util.function.Function
                          (apply [_ tr] (.set tr (.getBytes key) value) nil)))))

(defn get-bytes
  "Get a byte array value by key from FDB.
  Returns nil if the key does not exist."
  [^Database db ^String key]
  (error/try-nom :fdb/get-bytes
                 {:message "Failed to get bytes" :key key}
                 (.run db
                       (reify
                        java.util.function.Function
                          (apply [_ tr]
                            (some-> (.get tr (.getBytes key))
                                    .join))))))

(defn save-record!
  "Atomically saves a Java protobuf Message to the named record store
  and appends event-bytes to the transactional outbox. Both writes
  occur in a single FDB transaction and are automatically retried on
  conflict."
  [^FDBDatabase record-db store-name ^MessageLite record ^bytes event-bytes]
  (error/try-nom :fdb/save-record
                 {:message "Failed to save record" :store store-name}
                 (.run record-db
                       (reify
                        java.util.function.Function
                          (apply [_ ctx]
                            (let [fdb-store (store/open-record-store ctx
                                                                     store-name)
                                  tr (.ensureActive ctx)]
                              (.saveRecord fdb-store record)
                              (outbox/write-entry tr store-name event-bytes)
                              nil))))))
