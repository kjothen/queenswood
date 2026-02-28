(ns com.repldriven.mono.fdb.changelog.checkpoint
  (:refer-clojure :exclude [read])
  (:import
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase
                                                         FDBStoreTimer$Waits)
    (com.apple.foundationdb.tuple Versionstamp)
    (java.util.function Function)))

(defn read
  "Returns the Versionstamp of the last processed changelog entry for
  the given checkpoint key, or nil if no checkpoint exists yet."
  [^FDBDatabase record-db checkpoint-key]
  (.run record-db
        ^Function
        (fn [ctx]
          (some-> (.asyncToSync ctx
                                FDBStoreTimer$Waits/WAIT_LOAD_SYSTEM_KEY
                                (.get (.ensureActive ctx) checkpoint-key))
                  (Versionstamp/complete)))))

(defn write
  "Stores the raw bytes of vs as the checkpoint at checkpoint-key
  within the given transaction."
  [tr checkpoint-key ^Versionstamp vs]
  (.set tr checkpoint-key (.getBytes vs)))
