(ns com.repldriven.mono.fdb.outbox
  (:import
    (com.apple.foundationdb MutationType Transaction)
    (com.apple.foundationdb.subspace Subspace)
    (com.apple.foundationdb.tuple Tuple Versionstamp)))

(def ^:private root "mono")

(defn outbox-subspace
  [store-name]
  ;; ("mono", "outbox", "persons", <versionstamp>)
  (Subspace. (Tuple/from (into-array Object [root "outbox" store-name]))))

(defn sentinel-key
  "Returns the raw FDB key bytes for the outbox sentinel — a single key
  atomically incremented on every write-entry, suitable for FDB watches."
  [store-name]
  ;; ("mono", "sentinel", "persons")
  (.pack (Subspace. (Tuple/from (into-array Object
                                            [root "sentinel" store-name])))))

(defn checkpoint-key
  "Returns the raw FDB key bytes for the relay checkpoint — the last
  versionstamp successfully processed by the relay is stored here."
  [store-name]
  ;; ("mono", "checkpoint", "persons")
  (.pack (Subspace. (Tuple/from (into-array Object
                                            [root "checkpoint" store-name])))))

(defn write-entry
  "Atomically writes event-bytes to the outbox for store-name and bumps
  the sentinel. Uses SET_VERSIONSTAMPED_KEY so each entry is keyed by
  FDB commit version, giving a globally ordered, append-only outbox."
  [^Transaction tr store-name ^bytes event-bytes]
  (let [subspace (outbox-subspace store-name)
        key (.packWithVersionstamp subspace
                                   (Tuple/from (object-array
                                                [(Versionstamp/incomplete)])))]
    (.mutate tr MutationType/SET_VERSIONSTAMPED_KEY key event-bytes)
    (.mutate tr
             MutationType/ADD
             (sentinel-key store-name)
             (byte-array [1 0 0 0 0 0 0 0]))))
