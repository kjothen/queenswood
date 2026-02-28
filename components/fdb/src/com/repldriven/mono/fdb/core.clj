(ns com.repldriven.mono.fdb.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.outbox :as outbox]
    [com.repldriven.mono.fdb.store :as store])
  (:import
    (com.apple.foundationdb Database)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase)
    (com.apple.foundationdb.record.query RecordQuery)
    (com.apple.foundationdb.record.query.expressions Query)
    (com.apple.foundationdb.tuple Tuple)
    (com.google.protobuf MessageLite)))

(defn set-str
  "Set a string key-value pair in the FDB database."
  [^Database db ^String key ^String value]
  (error/try-nom
   :fdb/set-str
   {:message "Failed to set value" :key key}
   (.run db
         (reify
          java.util.function.Function
            (apply [_ tr] (.set tr (.getBytes key) (.getBytes value)) nil)))))

(defn get-str
  "Get a string value by key from the FDB database.
  Returns nil if the key does not exist."
  [^Database db ^String key]
  (error/try-nom :fdb/get-str
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

(defn watch-outbox
  "Sets up a watch on the outbox sentinel key for store-name.
  Returns a CompletableFuture<Void> that completes when the next
  outbox-record for this store is committed."
  [^FDBDatabase record-db store-name]
  (error/try-nom :fdb/watch-outbox
                 {:message "Failed to set up outbox watch" :store store-name}
                 (.run record-db
                       (reify
                        java.util.function.Function
                          (apply [_ ctx]
                            (.watch (.ensureActive ctx)
                                    (outbox/sentinel-key store-name)))))))

(defn load-record
  "Loads a record by primary key from the named record store.
  Returns the serialized bytes of the record, or nil if not
  found."
  [^FDBDatabase record-db open-store-fn store-name primary-key]
  (error/try-nom
   :fdb/load-record
   {:message "Failed to load record" :store store-name}
   (.run record-db
         (reify
          java.util.function.Function
            (apply [_ ctx]
              (let [fdb-store (store/open-store open-store-fn ctx store-name)]
                (some-> (.loadRecord fdb-store
                                     (Tuple/from (into-array Object
                                                             [primary-key])))
                        .getRecord
                        .toByteArray)))))))

(defn save-record
  "Saves a Java protobuf Message to the named record store."
  [^FDBDatabase record-db open-store-fn store-name ^MessageLite record]
  (error/try-nom :fdb/save-record
                 {:message "Failed to save record" :store store-name}
                 (.run record-db
                       (reify
                        java.util.function.Function
                          (apply [_ ctx]
                            (.saveRecord
                             (store/open-store open-store-fn ctx store-name)
                             record)
                            nil)))))

(defn outbox-record
  "Atomically saves a Java protobuf Message to the named record
  store and appends event-bytes to the transactional outbox. Both
  writes occur in a single FDB transaction and are automatically
  retried on conflict."
  [^FDBDatabase record-db open-store-fn store-name ^MessageLite record
   ^bytes event-bytes]
  (error/try-nom
   :fdb/outbox-record
   {:message "Failed to save record" :store store-name}
   (.run record-db
         (reify
          java.util.function.Function
            (apply [_ ctx]
              (let [fdb-store (store/open-store open-store-fn ctx store-name)
                    tr (.ensureActive ctx)]
                (.saveRecord fdb-store record)
                (outbox/write-entry tr store-name event-bytes)
                nil))))))

(defn store-load
  "Loads a record by primary key from an open FDBRecordStore.
  Returns serialized bytes or nil. For use inside transact."
  [fdb-store primary-key]
  (some-> (.loadRecord fdb-store (Tuple/from (into-array Object [primary-key])))
          .getRecord
          .toByteArray))

(defn store-save
  "Saves a Java protobuf Message to an open FDBRecordStore.
  For use inside transact."
  [fdb-store ^MessageLite record]
  (.saveRecord fdb-store record)
  nil)

(defn store-query
  "Queries an open FDBRecordStore where field equals value.
  Returns a vector of serialized byte arrays. For use inside
  transact."
  [fdb-store record-type field value]
  (let [query (-> (RecordQuery/newBuilder)
                  (.setRecordType record-type)
                  (.setFilter (-> (Query/field field)
                                  (.equalsValue value)))
                  .build)]
    (->> (.executeQuery fdb-store query)
         .asList
         .join
         (mapv #(-> %
                    .getRecord
                    .toByteArray)))))

(defn transact
  "Runs f within a single FDB Record Layer transaction. f receives
  the FDBRecordContext and should return the transaction result."
  [^FDBDatabase record-db f]
  (error/try-nom :fdb/transact
                 "Failed to execute transaction"
                 (.run record-db
                       (reify
                        java.util.function.Function
                          (apply [_ ctx] (f ctx))))))

(defn query-records
  "Queries records in the named store where field equals value.
  Returns a vector of serialized byte arrays for matching records."
  [^FDBDatabase record-db open-store-fn store-name record-type field value]
  (error/try-nom
   :fdb/query-records
   {:message "Failed to query records" :store store-name :field field}
   (.run record-db
         (reify
          java.util.function.Function
            (apply [_ ctx]
              (let [fdb-store (store/open-store open-store-fn ctx store-name)
                    query (-> (RecordQuery/newBuilder)
                              (.setRecordType record-type)
                              (.setFilter (-> (Query/field field)
                                              (.equalsValue value)))
                              .build)]
                (->> (.executeQuery fdb-store query)
                     .asList
                     .join
                     (mapv #(-> %
                                .getRecord
                                .toByteArray)))))))))
