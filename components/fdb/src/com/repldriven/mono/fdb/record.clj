(ns com.repldriven.mono.fdb.record
  (:refer-clojure :exclude [load])
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]])
  (:import
    (com.apple.foundationdb.record EndpointType
                                   ExecuteProperties
                                   IndexScanType
                                   ScanProperties
                                   TupleRange)
    (com.apple.foundationdb.record.provider.foundationdb
     FDBDatabase
     FDBStoreTimer$Waits
     IndexScanRange)
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

(defn- apply-allowed-indexes
  "Constrains the planner to the named index when
  (:index opts) is provided. Returns the builder."
  [builder opts]
  (let [index (:index opts)]
    (cond-> builder
            index
            (.setAllowedIndexes
             ^java.util.List
             (java.util.ArrayList. ^java.util.Collection [index])))))

(defn- equals-query
  ([record-type field value]
   (equals-query record-type field value nil))
  ([record-type field value opts]
   (-> (RecordQuery/newBuilder)
       (.setRecordType record-type)
       (.setFilter (field-filter [field value]))
       (apply-allowed-indexes opts)
       .build)))

(defn- and-query
  ([record-type filters]
   (and-query record-type filters nil))
  ([record-type filters opts]
   (-> (RecordQuery/newBuilder)
       (.setRecordType record-type)
       (.setFilter (Query/and
                    ^java.util.List
                    (java.util.ArrayList. (map field-filter filters))))
       (apply-allowed-indexes opts)
       .build)))

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
  transact. opts supports :index to pin the planner to a
  named index."
  ([store record-type field value]
   (query store record-type field value nil))
  ([store record-type field value opts]
   (mapv record->bytes
         (execute-query store (equals-query record-type field value opts)))))

(defn query-one
  "Queries an open FDBRecordStore where field equals value,
  capping the planner at one result via ExecuteProperties.
  Returns the first matching record bytes, or nil. opts
  supports :index to pin the planner to a named index."
  ([store record-type field value]
   (query-one store record-type field value nil))
  ([store record-type field value opts]
   (some-> (execute-query-one store
                              (equals-query record-type field value opts))
           first
           record->bytes)))

(defn query-one-compound
  "Queries an open FDBRecordStore where all of a sequence of
  [field value] pairs match, capping the planner at one
  result. Returns first matching record bytes, or nil.
  opts supports :index to pin the planner to a named index."
  ([store record-type filters]
   (query-one-compound store record-type filters nil))
  ([store record-type filters opts]
   (some-> (execute-query-one store (and-query record-type filters opts))
           first
           record->bytes)))

(defn- map-entry-query
  [record-type map-field map-key map-value opts]
  (-> (RecordQuery/newBuilder)
      (.setRecordType record-type)
      (.setFilter
       (-> (Query/field map-field)
           .oneOfThem
           (.matches
            (Query/and ^java.util.List
                       (java.util.ArrayList.
                        [(.equalsValue (Query/field "key") map-key)
                         (.equalsValue (Query/field "value") map-value)])))))
      (apply-allowed-indexes opts)
      .build))

(defn query-by-map-entry
  "Queries records where a proto map<K,V> field has at least
  one entry matching `map-key`/`map-value`. Returns a vector
  of serialized byte arrays. opts supports :index to pin the
  planner to a named index."
  ([store record-type map-field map-key map-value]
   (query-by-map-entry store record-type map-field map-key map-value nil))
  ([store record-type map-field map-key map-value opts]
   (mapv record->bytes
         (execute-query store
                        (map-entry-query record-type
                                         map-field
                                         map-key
                                         map-value
                                         opts)))))

(defn count-groups
  "Counts unique grouping-key entries in a COUNT index whose
  group key starts with `prefix`. Returns the number of
  distinct groups, not the sum of per-group counts. Uses a
  `BY_GROUP` index scan: FDB iterates the index range
  server-side and streams one entry per group; only index
  entries cross the wire."
  [store index-name prefix]
  (let [index (.getIndex (.getRecordMetaData store) index-name)
        prefix-tuple (if (vector? prefix)
                       (Tuple/from (into-array Object prefix))
                       (Tuple/from (into-array Object [prefix])))
        bounds (IndexScanRange. IndexScanType/BY_GROUP
                                (TupleRange/allOf prefix-tuple))]
    (-> (.scanIndex store index bounds nil ScanProperties/FORWARD_SCAN)
        .getCount
        .join)))

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
    whose store-opening is memoised for this transaction.

  If f returns an anomaly, the FDB transaction is aborted
  (rolled back) and the anomaly is returned to the caller."
  ([txn-or-config f]
   (transact txn-or-config f :fdb/transact "Failed to execute transaction"))
  ([txn-or-config f category message]
   (if (instance? Txn txn-or-config)
     (try-nom category message (f txn-or-config))
     (let [{:keys [record-db record-store]} txn-or-config]
       (try
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
                                       s)))
                       result (try-nom category
                                       message
                                       (f (->Txn open-fn)))]
                   (if (error/anomaly? result)
                     (throw (ex-info "Transaction rolled back"
                                     {::anomaly result}))
                     result))))
         (catch Exception e
           (or (::anomaly (ex-data e))
               (error/fail category
                           {:message message
                            :exception e
                            :stack-trace
                            (with-out-str
                              (.printStackTrace
                               e
                               (java.io.PrintWriter. *out*
                                                     true)))}))))))))

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
  `{:records [bytes ...] :before cursor|nil :after cursor|nil}`,
  where `:records` is in the requested display order.

  `:before` is the cursor of the first record in the page (what the
  client should send back as `:before` to page *previous*). `:after`
  is the cursor of the last record — only set when more rows exist
  beyond the page — for the client to send back as `:after` to page
  *next*. Both are phrased in the client's display direction, so
  `page[after]` / `page[before]` always mean next / prev regardless
  of whether the natural order is ascending or descending.

  opts:
    :prefix  vector of leading PK parts to scope the scan
    :after   cursor, client's \"next page\" boundary
    :before  cursor, client's \"previous page\" boundary
    :limit   int, page size
    :order   `:asc` (default) or `:desc` — selects the display
             direction; in `:desc` the first page (no cursor)
             returns the highest-keyed records first

  When `:prefix` is given, the scan is constrained to records whose
  PK starts with those values. Cursors are the PK element at the
  position after the prefix."
  [store {:keys [prefix after before limit order]}]
  (let [descending? (= :desc order)
        ;; Translate client-oriented cursors into native range bounds.
        ;; In asc, `:after X` is a low exclusive bound (forward from X+ε);
        ;; `:before X` is a high exclusive bound (reverse to X-ε). In
        ;; desc, the roles swap — "next after X" now means "keys less
        ;; than X", and "prev before X" means "keys greater than X".
        low-cursor (if descending? before after)
        high-cursor (if descending? after before)
        ;; Scan backward when the natural traversal opposes key order:
        ;; asc + `:before` (paginating back from a higher cursor), or
        ;; desc without a low-cursor (default desc scan runs from the
        ;; end down).
        reverse-scan? (if descending?
                        (nil? low-cursor)
                        (some? high-cursor))
        prefix-size (count (or prefix []))
        prefix-tuple (when (seq prefix)
                       (Tuple/from (into-array Object prefix)))
        base-range (when prefix-tuple
                     (prefix-range prefix-tuple))
        range (cond
               (and prefix-tuple low-cursor)
               (TupleRange.
                (cursor-tuple prefix low-cursor)
                (.getHigh ^TupleRange base-range)
                EndpointType/RANGE_EXCLUSIVE
                (.getHighEndpoint ^TupleRange
                                  base-range))

               (and prefix-tuple high-cursor)
               (TupleRange.
                (.getLow ^TupleRange base-range)
                (cursor-tuple prefix high-cursor)
                (.getLowEndpoint ^TupleRange
                                 base-range)
                EndpointType/RANGE_EXCLUSIVE)

               prefix-tuple
               base-range

               low-cursor
               (TupleRange.
                (Tuple/from (into-array Object [low-cursor]))
                nil
                EndpointType/RANGE_EXCLUSIVE
                EndpointType/TREE_END)

               high-cursor
               (TupleRange.
                nil
                (Tuple/from (into-array Object [high-cursor]))
                EndpointType/TREE_START
                EndpointType/RANGE_EXCLUSIVE)

               :else
               TupleRange/ALL)
        execute-props (-> (ExecuteProperties/newBuilder)
                          (.setReturnedRowLimit (inc limit))
                          .build)
        scan-props (ScanProperties. execute-props reverse-scan?)
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
        trimmed (cond-> raw more? (subvec 0 limit))
        ;; Native scan produces records low-to-high on forward and
        ;; high-to-low on reverse. Flip only when the scan direction
        ;; disagrees with the display direction.
        page (if (= reverse-scan? descending?)
               trimmed
               (vec (rseq trimmed)))]
    {:records (mapv record->bytes page)
     :before (when (seq page)
               (cursor (first page) prefix-size))
     :after (when more?
              (cursor (peek page) prefix-size))}))
