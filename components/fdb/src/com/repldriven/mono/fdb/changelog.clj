(ns com.repldriven.mono.fdb.changelog
  (:import
    (com.apple.foundationdb.subspace Subspace)
    (com.apple.foundationdb.tuple Tuple)))

(def ^:private root "mono")

(defn changelog-subspace
  "Returns the Subspace for the changelog of store-name. Entries are
  keyed by versionstamp — (commit-version, user-version) — giving a
  globally ordered, append-only log. Scanning from a checkpoint
  forward is an efficient range read with no secondary index needed."
  [store-name]
  ;; ("mono", "changelog", "accounts", <versionstamp>)
  (Subspace. (Tuple/from (into-array Object [root "changelog" store-name]))))

(defn sentinel-key
  "Returns the raw FDB key bytes for the changelog sentinel — a single key
  atomically incremented on every write, suitable for FDB watches."
  [store-name]
  ;; ("mono", "sentinel", "accounts")
  (.pack (Subspace. (Tuple/from (into-array Object
                                            [root "sentinel" store-name])))))

(defn checkpoint-key
  "Returns the raw FDB key bytes for a per-consumer checkpoint — each
  consumer tracks the last versionstamp it processed independently."
  [consumer-id store-name]
  ;; ("mono", "checkpoint", "my-consumer", "accounts")
  (.pack (Subspace. (Tuple/from (into-array Object
                                            [root "checkpoint" consumer-id
                                             store-name])))))
