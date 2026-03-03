(ns com.repldriven.mono.fdb.record
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.error.interface :as error])
  (:import
    (com.apple.foundationdb.record EndpointType
                                   ExecuteProperties
                                   ScanProperties
                                   TupleRange)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase
                                                         FDBStoreTimer$Waits)
    (com.apple.foundationdb.record.query RecordQuery)
    (com.apple.foundationdb.record.query.expressions Query)
    (com.apple.foundationdb.tuple Tuple)
    (com.google.protobuf MessageLite)
    (java.util.function Function)))

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

(defn transact
  "Opens an FDB Record Layer store and runs f within a single
  transaction. f receives the open FDBRecordStore and should
  return the transaction result.

  The 6-arg form accepts a custom nom category and message for
  call-site-specific anomaly reporting."
  ([^FDBDatabase record-db open-store-fn store-name f]
   (transact record-db
             open-store-fn
             store-name
             f
             :fdb/transact
             "Failed to execute transaction"))
  ([^FDBDatabase record-db open-store-fn store-name f category message]
   (error/try-nom
    category
    message
    (.run record-db
          ^Function (fn [ctx] (f (open-store open-store-fn ctx store-name)))))))

(defn transact-multi
  "Runs f within a single FDB transaction, passing a function
  that opens stores by name. f receives open-store and should
  call (open-store \"store-name\") for each store it needs.
  All writes across stores are atomic."
  ([^FDBDatabase record-db open-store-fn f]
   (transact-multi record-db
                   open-store-fn
                   f
                   :fdb/transact
                   "Failed to execute transaction"))
  ([^FDBDatabase record-db open-store-fn f category message]
   (error/try-nom category
                  message
                  (.run record-db
                        ^Function
                        (fn [ctx]
                          (f (fn [store-name]
                               (open-store open-store-fn ctx store-name))))))))

(defn scan
  "Scans records by primary key order. Returns
  {:records [bytes ...] :has-more boolean}.

  opts:
    :after   primary-key string, exclusive lower bound (forward)
    :before  primary-key string, exclusive upper bound (reverse)
    :limit   int, page size

  When :after is given, scans forward from that key. When :before
  is given, scans backward from that key, results returned in
  forward order. When neither, scans from the start."
  [store {:keys [after before limit]}]
  (let [reverse? (some? before)
        range (cond
               after
               (TupleRange.
                (Tuple/from (into-array Object [after]))
                nil
                EndpointType/RANGE_EXCLUSIVE
                EndpointType/TREE_END)
               before
               (TupleRange.
                nil
                (Tuple/from
                 (into-array Object [before]))
                EndpointType/TREE_START
                EndpointType/RANGE_EXCLUSIVE)
               :else
               TupleRange/ALL)
        execute-props (-> (ExecuteProperties/newBuilder)
                          (.setReturnedRowLimit (inc limit))
                          .build)
        scan-props (ScanProperties. execute-props reverse?)
        records
        (->>
          (.scanRecords store ^TupleRange range nil ^ScanProperties scan-props)
          .asList
          (.asyncToSync (.getContext store)
                        FDBStoreTimer$Waits/WAIT_SCAN_RECORDS)
          (mapv record->bytes))
        has-more (> (count records) limit)
        page (cond-> (if has-more (subvec records 0 limit) records)
               reverse? rseq
               reverse? vec)]
    {:records page :has-more has-more}))
