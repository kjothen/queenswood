(ns com.repldriven.mono.fdb.consumer
  (:require
    [com.repldriven.mono.fdb.changelog :as changelog]
    [com.repldriven.mono.fdb.store :as store])
  (:import
    (com.apple.foundationdb KeySelector)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase)
    (com.apple.foundationdb.tuple Tuple Versionstamp)))

(defn- read-checkpoint
  "Returns the Versionstamp of the last processed changelog entry for
  consumer-id in store-name, or nil if no checkpoint exists yet."
  [^FDBDatabase record-db consumer-id store-name]
  (.run record-db
        (reify
         java.util.function.Function
           (apply [_ ctx]
             (some-> (.get (.ensureActive ctx)
                           (changelog/checkpoint-key consumer-id store-name))
                     .join
                     (Versionstamp/complete))))))

(defn- write-checkpoint
  "Stores the raw bytes of vs as the checkpoint for consumer-id in
  store-name within the given transaction."
  [tr consumer-id store-name ^Versionstamp vs]
  (.set tr (changelog/checkpoint-key consumer-id store-name) (.getBytes vs)))

(defn- scan-changelog
  "Returns a Java List of KeyValues from the changelog for store-name
  that come strictly after from-vs, or all entries when from-vs is nil."
  [ctx store-name from-vs]
  (let [subspace (changelog/changelog-subspace store-name)
        begin (if from-vs
                (KeySelector/firstGreaterThan
                 (.pack subspace (Tuple/from (object-array [from-vs]))))
                (KeySelector/firstGreaterOrEqual (.pack subspace)))
        end (KeySelector/firstGreaterOrEqual (-> subspace
                                                 .range
                                                 .end))]
    (-> (.getRange (.ensureActive ctx) begin end)
        .asList
        .join)))

(defn- ctx-load-record
  "Loads a record by id from the named store within an existing context.
  Returns serialized bytes or nil if not found."
  [open-store-fn ctx store-name record-id]
  (let [fdb-store (store/open-store open-store-fn ctx store-name)]
    (some-> (.loadRecord fdb-store (Tuple/from (into-array Object [record-id])))
            .getRecord
            .toByteArray)))

(defn process-changelog
  "Reads unprocessed changelog entries for consumer-id in store-name,
  calls (handler serialized-bytes) for each, and advances the
  checkpoint to the last versionstamp seen. All reads and the
  checkpoint write occur in a single transaction."
  [^FDBDatabase record-db open-store-fn consumer-id store-name handler]
  (.run
   record-db
   (reify
    java.util.function.Function
      (apply [_ ctx]
        (let [tr (.ensureActive ctx)
              checkpoint (read-checkpoint record-db consumer-id store-name)
              entries (scan-changelog ctx store-name checkpoint)]
          (when (seq entries)
            (doseq [kv entries]
              (let [record-id (String. (.getValue kv))
                    record
                    (ctx-load-record open-store-fn ctx store-name record-id)]
                (handler record)))
            (let [subspace (changelog/changelog-subspace store-name)
                  last-vs (.getVersionstamp (.unpack subspace
                                                     (.getKey (last entries)))
                                            0)]
              (write-checkpoint tr consumer-id store-name last-vs)))
          nil)))))
