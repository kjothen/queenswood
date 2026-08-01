(ns com.repldriven.queenswood.fdb.interface
  (:require
    [com.repldriven.queenswood.fdb.system.core]

    [com.repldriven.queenswood.fdb.changelog :as changelog]
    [com.repldriven.queenswood.fdb.check :as check]
    [com.repldriven.queenswood.fdb.counter :as counter]
    [com.repldriven.queenswood.fdb.kv :as kv]
    [com.repldriven.queenswood.fdb.merge :as merge]
    [com.repldriven.queenswood.fdb.record :as record]
    [com.repldriven.queenswood.fdb.scan :as scan]))

;; KV layer

(defn set-str [db key value] (kv/set-str db key value))

(defn get-str [db key] (kv/get-str db key))

(defn set-bytes [db key value] (kv/set-bytes db key value))

(defn get-bytes [db key] (kv/get-bytes db key))

;; Record layer

(defn load-record
  [store & primary-key-parts]
  (apply record/load store primary-key-parts))

(defn save-record [store record] (record/save store record))

(defn delete-record
  "Deletes a record by primary key from an open FDBRecordStore.
  Returns true if deleted, false if not found."
  [store & primary-key-parts]
  (apply record/delete store primary-key-parts))

(defn query-records
  "Queries an open FDBRecordStore where field equals value.
  Returns a vector of serialized byte arrays. opts supports
  :index to pin the planner to a named index."
  ([store record-type field value]
   (record/query store record-type field value))
  ([store record-type field value opts]
   (record/query store record-type field value opts)))

(defn query-record
  "Queries an open FDBRecordStore where field equals value,
  capping the planner at one result. Returns the first
  matching record bytes, or nil. opts supports :index to
  pin the planner to a named index."
  ([store record-type field value]
   (record/query-one store record-type field value))
  ([store record-type field value opts]
   (record/query-one store record-type field value opts)))

(defn query-record-compound
  "Queries an open FDBRecordStore where all [field value]
  pairs match, capping the planner at one result. Returns
  the first matching record bytes, or nil. opts supports
  :index to pin the planner to a named index."
  ([store record-type filters]
   (record/query-one-compound store record-type filters))
  ([store record-type filters opts]
   (record/query-one-compound store record-type filters opts)))

(defn query-records-by-map-entry
  "Queries records where a proto map field has at least one
  entry matching `map-key`/`map-value`. Returns a vector of
  serialized byte arrays. opts supports :index to pin the
  planner to a named index."
  ([store record-type map-field map-key map-value]
   (record/query-by-map-entry store record-type map-field map-key map-value))
  ([store record-type map-field map-key map-value opts]
   (record/query-by-map-entry store
                              record-type
                              map-field
                              map-key
                              map-value
                              opts)))

(defn count-records
  [store index-name key]
  (record/count-records store index-name key))

(defn count-records-snapshot
  "`count-records`, read at SNAPSHOT so the aggregate key does not join
  the transaction's read-conflict set. For a safety-net limit, where a
  count stale by the in-flight writers is acceptable and serialising
  the whole group on the counter is not."
  [store index-name key]
  (record/count-records store index-name key {:isolation :snapshot}))

(defn sum-records
  "Sums the trailing value column of a SUM index over the group
  whose grouping key is `key`. O(1) via the aggregate index; an
  empty group sums to 0."
  [store index-name key]
  (record/sum-records store index-name key))

(defn count-groups
  "Counts distinct grouping-key entries in a COUNT index whose
  group key starts with `prefix` — one per group, not the sum
  of per-group counts."
  [store index-name prefix]
  (record/count-groups store index-name prefix))


(defn scan-records [store opts] (scan/scan store opts))

(defn scan-record-entries
  "As `scan-records`, but each record comes back as
  `{:key cursor :record bytes}` so a caller can pair records from two
  stores without deserialising them."
  [store opts]
  (scan/scan-entries store opts))

(defn merge-scan
  "Pairs two stores on the first key element past their prefixes and
  reduces over the pairs in key order. Takes the config rather than
  opened stores, because each page refill needs its own transaction.

  An outer join: a key in one store but not the other still reaches
  `f`, with the absent side empty. Not a consistent snapshot — the
  sides refill in separate transactions.

  Args:
  - config: map with :record-db and :record-store.
  - opts: `{:left {:store :prefix :limit} :right {...}}`.
  - f: reducing fn of `[acc {:keys [key left right]}]`, where the
    records are bytes. May return `reduced`; an anomaly propagates.
  - init: initial accumulator."
  [config opts f init]
  (merge/merge-scan config opts f init))

(defn write-changelog
  "Writes a changelog entry for record-id in store-name. Takes the Txn
  rather than an opened store so the keyspace prefix travels with it;
  the store open is memoised for the transaction, so naming it here
  costs nothing."
  [txn store-name record-id changelog-bytes]
  (changelog/write (record/open txn store-name)
                   (:prefix txn)
                   store-name
                   record-id
                   changelog-bytes))

(defn process-changelog
  ([record-db consumer-id store-name handler]
   (changelog/process record-db consumer-id store-name handler))
  ([record-db consumer-id store-name handler opts]
   (changelog/process record-db consumer-id store-name handler opts)))

(defn allocate-counter
  "Allocates the next value of the counter at key-parts within
  store-name. Takes the Txn for the same reason as write-changelog."
  [txn store-name & key-parts]
  (apply counter/allocate
         (record/open txn store-name)
         (:prefix txn)
         key-parts))

(defn transact
  "Runs f within a transaction. f receives a Txn. Given an
  existing Txn, reuses it; given a config map with
  :record-db and :record-store, opens a fresh FDB
  transaction."
  ([txn-or-config f]
   (record/transact txn-or-config f))
  ([txn-or-config f category message]
   (record/transact txn-or-config f category message)))

(defn open
  "Opens a named store within the transaction."
  [txn store-name]
  (record/open txn store-name))

(defn ctx->txn
  "Adapts a raw FDB context into a Txn so store fns can be
  called from within a handler that owns its own ctx
  (e.g. a changelog relay handler). open-store-fn takes
  [ctx store-name] and returns an opened FDBRecordStore;
  opens are memoised for the life of the Txn. The keyspace prefix
  comes off open-store-fn's metadata, so a handler that owns its own
  ctx still writes into its system's keyspace."
  [ctx open-store-fn]
  (let [cache (atom {})]
    (record/->Txn (fn [store-name]
                    (or (get @cache store-name)
                        (let [s (open-store-fn ctx store-name)]
                          (swap! cache assoc store-name s)
                          s)))
                  (:keyspace-prefix (meta open-store-fn)))))

(def txn? check/txn?)

(def uniqueness-violation? check/uniqueness-violation?)
