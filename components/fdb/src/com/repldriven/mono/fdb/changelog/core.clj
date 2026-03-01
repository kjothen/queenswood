(ns com.repldriven.mono.fdb.changelog.core
  (:require
    [com.repldriven.mono.fdb.changelog.checkpoint :as checkpoint]
    [com.repldriven.mono.fdb.record :as record])
  (:import
    (com.apple.foundationdb KeySelector MutationType)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase
                                                         FDBStoreTimer$Waits)
    (com.apple.foundationdb.subspace Subspace)
    (com.apple.foundationdb.tuple Tuple Versionstamp)
    (java.util.function Function)))

(def ^:private root "mono")

(defn- changelog-subspace
  "Returns the Subspace for the changelog of store-name. Entries are
  keyed by versionstamp — (commit-version, user-version) — giving a
  globally ordered, append-only log. Scanning from a checkpoint
  forward is an efficient range read with no secondary index needed."
  [store-name]
  ;; ("mono", "changelog", "accounts", <versionstamp>)
  (Subspace. (Tuple/from (into-array Object [root "changelog" store-name]))))

(defn- sentinel-key
  "Returns the raw FDB key bytes for the changelog sentinel — a single key
  atomically incremented on every write, suitable for FDB watches."
  [store-name]
  ;; ("mono", "sentinel", "accounts")
  (.pack (Subspace. (Tuple/from (into-array Object
                                            [root "sentinel" store-name])))))

(defn- checkpoint-key
  "Returns the raw FDB key bytes for a per-consumer checkpoint — each
  consumer tracks the last versionstamp it processed independently."
  [consumer-id store-name]
  ;; ("mono", "checkpoint", "my-consumer", "accounts")
  (.pack (Subspace. (Tuple/from (into-array Object
                                            [root "checkpoint" consumer-id
                                             store-name])))))

(defn write
  "Writes a versionstamped changelog entry for record-id and bumps the
  sentinel for store-name within an existing FDBRecordContext. The
  record-id is stored as the value so consumers can fetch the record
  by id. Uses claimLocalVersion to assign a unique user version per
  call within the same runion. For use inside run."
  [ctx store-name ^String record-id]
  (let [tr (.ensureActive ctx)
        user-ver (.claimLocalVersion ctx)]
    (.mutate tr
             MutationType/SET_VERSIONSTAMPED_KEY
             (.packWithVersionstamp
              (changelog-subspace store-name)
              (Tuple/from (object-array [(Versionstamp/incomplete user-ver)])))
             (.getBytes record-id))
    (.mutate tr
             MutationType/ADD
             (sentinel-key store-name)
             (byte-array [1 0 0 0 0 0 0 0]))))

(defn- scan
  "Returns a Java List of KeyValues from the changelog for store-name
  that come strictly after from-vs, or all entries when from-vs is nil."
  [ctx store-name from-vs]
  (let [subspace (changelog-subspace store-name)
        begin (if from-vs
                (KeySelector/firstGreaterThan
                 (.pack subspace (Tuple/from (object-array [from-vs]))))
                (KeySelector/firstGreaterOrEqual (.pack subspace)))
        end (KeySelector/firstGreaterOrEqual (-> subspace
                                                 .range
                                                 .end))]
    (.asyncToSync ctx
                  FDBStoreTimer$Waits/WAIT_SCAN_RECORDS
                  (-> (.getRange (.ensureActive ctx) begin end)
                      .asList))))

(defn- deduplicate
  "Keeps only the latest changelog entry per record-id. Since entries
  are ordered by versionstamp, last within each group is the most
  recent write for that record."
  [entries]
  (->> entries
       (group-by (fn [kv] (String. (.getValue kv))))
       vals
       (map last)))

(defn process
  "Reads unprocessed changelog entries for consumer-id in store-name,
  calls (handler serialized-bytes) for each, and advances the
  checkpoint to the last versionstamp seen. All reads and the
  checkpoint write occur in a single runion.

  Options:
    :deduplicate? (default true) — when true, only the latest entry
    per record-id is processed. Set to false for audit consumers that
    need every write."
  ([^FDBDatabase record-db open-store-fn consumer-id store-name handler]
   (process record-db open-store-fn consumer-id store-name handler {}))
  ([^FDBDatabase record-db open-store-fn consumer-id store-name handler opts]
   (let [{:keys [deduplicate?] :or {deduplicate? true}} opts]
     (.run record-db
           ^Function
           (fn [ctx]
             (let [tr (.ensureActive ctx)
                   cp (checkpoint/read record-db
                                       (checkpoint-key consumer-id store-name))
                   entries (scan ctx store-name cp)]
               (when (seq entries)
                 (doseq [kv (cond-> entries deduplicate? deduplicate)]
                   (let [record-id (String. (.getValue kv))
                         record (record/load (record/open-store open-store-fn
                                                                ctx
                                                                store-name)
                                             record-id)]
                     (handler record)))
                 (let [subspace (changelog-subspace store-name)
                       last-vs (.getVersionstamp
                                (.unpack subspace (.getKey (last entries)))
                                0)]
                   (checkpoint/write tr
                                     (checkpoint-key consumer-id store-name)
                                     last-vs)))
               nil))))))

