(ns com.repldriven.mono.fdb.outbox
  (:import
    (com.apple.foundationdb MutationType Transaction)
    (com.apple.foundationdb.subspace Subspace)
    (com.apple.foundationdb.tuple Tuple Versionstamp)))

(defn- outbox-subspace
  [store-name]
  (Subspace. (Tuple/from (object-array ["outbox" store-name]))))

(defn write-entry
  "Atomically writes event-bytes to the outbox for store-name.
  Uses SET_VERSIONSTAMPED_KEY so each entry is keyed by FDB commit
  version, giving a globally ordered, append-only outbox."
  [^Transaction tr store-name ^bytes event-bytes]
  (let [subspace (outbox-subspace store-name)
        key (.packWithVersionstamp subspace
                                   (Tuple/from (object-array
                                                [(Versionstamp/incomplete)])))]
    (.mutate tr MutationType/SET_VERSIONSTAMPED_KEY key event-bytes)))
