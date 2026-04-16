(ns com.repldriven.mono.fdb.record
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.error.interface :refer [try-nom]])
  (:import
    (com.apple.foundationdb.record EndpointType
                                   ExecuteProperties
                                   ScanProperties
                                   TupleRange)
    (com.apple.foundationdb.record.provider.foundationdb
     FDBDatabase
     FDBStoreTimer$Waits)
    (com.apple.foundationdb.record.metadata IndexAggregateFunction
                                            IndexTypes)
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
  Returns serialized bytes or nil. For use inside transact.
  Accepts one or more primary key parts for composite keys."
  [store & primary-key-parts]
  (some-> (.loadRecord store (Tuple/from (into-array Object primary-key-parts)))
          record->bytes))

(defn save
  "Saves a Java protobuf Message to an open FDBRecordStore.
  For use inside transact."
  [store ^MessageLite record]
  (.saveRecord store record)
  nil)

(defn- field-filter
  [[field value]]
  (-> (Query/field field)
      (.equalsValue value)))

(defn- equals-query
  [record-type field value]
  (-> (RecordQuery/newBuilder)
      (.setRecordType record-type)
      (.setFilter (field-filter [field value]))
      .build))

(defn- and-query
  [record-type filters]
  (-> (RecordQuery/newBuilder)
      (.setRecordType record-type)
      (.setFilter (Query/and
                   ^java.util.List
                   (java.util.ArrayList. (map field-filter filters))))
      .build))

(defn- execute-query
  [store q]
  (->> (.executeQuery store q)
       .asList
       (.asyncToSync (.getContext store)
                     FDBStoreTimer$Waits/WAIT_EXECUTE_QUERY)))

(defn- execute-query-one
  [store q]
  (let [props (-> (ExecuteProperties/newBuilder)
                  (.setReturnedRowLimit 1)
                  .build)]
    (->> (.executeQuery store q nil props)
         .asList
         (.asyncToSync (.getContext store)
                       FDBStoreTimer$Waits/WAIT_EXECUTE_QUERY))))

(defn query
  "Queries an open FDBRecordStore where field equals value.
  Returns a vector of serialized byte arrays. For use inside
  transact."
  [store record-type field value]
  (mapv record->bytes
        (execute-query store (equals-query record-type field value))))

(defn query-compound
  "Queries an open FDBRecordStore where multiple fields
  equal values. filters is a sequence of [field value]
  pairs. Returns a vector of serialized byte arrays."
  [store record-type filters]
  (mapv record->bytes (execute-query store (and-query record-type filters))))

(defn query-one
  "Queries an open FDBRecordStore where field equals value,
  capping the planner at one result via ExecuteProperties.
  Returns the first matching record bytes, or nil."
  [store record-type field value]
  (some-> (execute-query-one store (equals-query record-type field value))
          first
          record->bytes))

(defn query-one-compound
  "Queries an open FDBRecordStore where all of a sequence of
  [field value] pairs match, capping the planner at one
  result. Returns first matching record bytes, or nil."
  [store record-type filters]
  (some-> (execute-query-one store (and-query record-type filters))
          first
          record->bytes))

(defn count-records
  "Counts records using a COUNT index. index-name is the
  name of the count index. key is the Tuple key to count
  (e.g. a single value or vector of values for compound
  keys). Uses evaluateAggregateFunction for O(1) lookup."
  [store index-name key]
  (let [key-tuple (if (vector? key)
                    (Tuple/from (into-array Object key))
                    (Tuple/from (into-array Object [key])))
        index (.getIndex (.getRecordMetaData store)
                         index-name)
        agg-fn (IndexAggregateFunction.
                IndexTypes/COUNT
                (.getRootExpression index)
                index-name)]
    (-> (.evaluateAggregateFunction
         store
         (java.util.Collections/emptyList)
         agg-fn
         (TupleRange/allOf key-tuple)
         com.apple.foundationdb.record.IsolationLevel/SERIALIZABLE)
        (.join)
        (.getLong 0))))

(defn query-repeated
  "Queries an open FDBRecordStore where a repeated field
  contains value. Uses oneOfThem() semantics for fan-out
  indexes. Returns a vector of serialized byte arrays.
  For use inside transact."
  [store record-type field value]
  (let [q (-> (RecordQuery/newBuilder)
              (.setRecordType record-type)
              (.setFilter (-> (Query/field field)
                              .oneOfThem
                              (.equalsValue value)))
              .build)]
    (->> (.executeQuery store q)
         .asList
         (.asyncToSync (.getContext store)
                       FDBStoreTimer$Waits/WAIT_EXECUTE_QUERY)
         (mapv record->bytes))))

(defrecord Txn [open])

(defn open
  "Opens a named store within the transaction. Memoised for
  the life of the txn so the same store name returns the same
  FDBRecordStore."
  [^Txn txn store-name]
  ((:open txn) store-name))

(defn transact
  "Runs f within a transaction. f receives a Txn.

  - Given an existing Txn, reuses it (composition).
  - Given a config map with :record-db and :record-store,
    opens a fresh FDB transaction and wraps ctx in a new Txn
    whose store-opening is memoised for this transaction."
  ([txn-or-config f]
   (transact txn-or-config f :fdb/transact "Failed to execute transaction"))
  ([txn-or-config f category message]
   (if (instance? Txn txn-or-config)
     (try-nom category message (f txn-or-config))
     (let [{:keys [record-db record-store]} txn-or-config]
       (try-nom category
                message
                (.run ^FDBDatabase record-db
                      ^Function
                      (fn [ctx]
                        (let [cache (atom {})
                              open-fn (fn [store-name]
                                        (or (get @cache store-name)
                                            (let [s (open-store record-store
                                                                ctx
                                                                store-name)]
                                              (swap! cache assoc store-name s)
                                              s)))]
                          (f (->Txn open-fn))))))))))

(defn- prefix-range
  "Returns a TupleRange scoped to a prefix tuple."
  [prefix-tuple]
  (TupleRange/allOf prefix-tuple))

(defn- cursor-tuple
  "Builds a cursor Tuple from prefix parts and a cursor
  value."
  [prefix cursor]
  (let [parts (into (vec prefix) [cursor])]
    (Tuple/from (into-array Object parts))))

(defn- cursor
  "Extracts the cursor element from a record's primary
  key at the given position."
  [r position]
  (.get (.getPrimaryKey r) (int position)))

(defn scan
  "Scans records by primary key order. Returns
  {:records [bytes ...] :before cursor|nil :after cursor|nil}.

  :after is the cursor for the next forward page (nil when
  no more records). :before is the first record's cursor
  (nil when empty).

  opts:
    :prefix  vector of leading PK parts to scope the scan
    :after   cursor, exclusive lower bound (forward)
    :before  cursor, exclusive upper bound (reverse)
    :limit   int, page size

  When :prefix is given, the scan is constrained to records
  whose PK starts with those values. Cursors are the PK
  element at the position after the prefix."
  [store {:keys [prefix after before limit]}]
  (let [reverse? (some? before)
        prefix-size (count (or prefix []))
        prefix-tuple (when (seq prefix)
                       (Tuple/from (into-array Object prefix)))
        base-range (when prefix-tuple
                     (prefix-range prefix-tuple))
        range (cond
               (and prefix-tuple after)
               (TupleRange.
                (cursor-tuple prefix after)
                (.getHigh ^TupleRange base-range)
                EndpointType/RANGE_EXCLUSIVE
                (.getHighEndpoint ^TupleRange
                                  base-range))

               (and prefix-tuple before)
               (TupleRange.
                (.getLow ^TupleRange base-range)
                (cursor-tuple prefix before)
                (.getLowEndpoint ^TupleRange
                                 base-range)
                EndpointType/RANGE_EXCLUSIVE)

               prefix-tuple
               base-range

               after
               (TupleRange.
                (Tuple/from (into-array Object [after]))
                nil
                EndpointType/RANGE_EXCLUSIVE
                EndpointType/TREE_END)

               before
               (TupleRange.
                nil
                (Tuple/from (into-array Object [before]))
                EndpointType/TREE_START
                EndpointType/RANGE_EXCLUSIVE)

               :else
               TupleRange/ALL)
        execute-props (-> (ExecuteProperties/newBuilder)
                          (.setReturnedRowLimit (inc limit))
                          .build)
        scan-props (ScanProperties. execute-props reverse?)
        raw (->> (.scanRecords store
                               ^TupleRange range
                               nil
                               ^ScanProperties scan-props)
                 .asList
                 (.asyncToSync
                  (.getContext store)
                  FDBStoreTimer$Waits/WAIT_SCAN_RECORDS)
                 vec)
        more? (> (count raw) limit)
        page (cond->
              raw

              more?
              (subvec 0 limit)

              reverse?
              (-> rseq
                  vec))]
    {:records (mapv record->bytes page)
     :before (when (seq page)
               (cursor (first page) prefix-size))
     :after (when more?
              (cursor (peek page) prefix-size))}))
