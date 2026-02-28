(ns com.repldriven.mono.fdb.record
  (:refer-clojure :exclude [load])
  (:import
    (com.apple.foundationdb.record.provider.foundationdb FDBStoreTimer$Waits)
    (com.apple.foundationdb.record.query RecordQuery)
    (com.apple.foundationdb.record.query.expressions Query)
    (com.apple.foundationdb.tuple Tuple)
    (com.google.protobuf MessageLite)))

(defn open-store
  "Opens a record store by calling the store function (returned by
  the store or meta-store system component) with the given context
  and store name."
  [open-store-fn ctx store-name]
  (open-store-fn ctx store-name))

(defn- record->bytes
  [r]
  (-> r
      .getRecord
      .toByteArray))

(defn load
  "Loads a record by primary key from an open FDBRecordStore.
  Returns serialized bytes or nil. For use inside transact."
  [store primary-key]
  (some-> (.loadRecord store (Tuple/from (into-array Object [primary-key])))
          record->bytes))

(defn save
  "Saves a Java protobuf Message to an open FDBRecordStore.
  For use inside transact."
  [store ^MessageLite record]
  (.saveRecord store record)
  nil)

(defn query
  "Queries an open FDBRecordStore where field equals value.
  Returns a vector of serialized byte arrays. For use inside
  transact."
  [store record-type field value]
  (let [q (-> (RecordQuery/newBuilder)
              (.setRecordType record-type)
              (.setFilter (-> (Query/field field)
                              (.equalsValue value)))
              .build)]
    (->> (.executeQuery store q)
         .asList
         (.asyncToSync (.getContext store)
                       FDBStoreTimer$Waits/WAIT_EXECUTE_QUERY)
         (mapv record->bytes))))
