(ns com.repldriven.mono.fdb.producer
  (:require
    [com.repldriven.mono.fdb.changelog :as changelog])
  (:import
    (com.apple.foundationdb MutationType)
    (com.apple.foundationdb.tuple Tuple Versionstamp)))

(defn write-changelog
  "Writes a versionstamped changelog entry for record-id and bumps the
  sentinel for store-name within an existing FDBRecordContext. The
  record-id is stored as the value so consumers can fetch the record
  by id. Uses claimLocalVersion to assign a unique user version per
  call within the same transaction. For use inside transact."
  [ctx store-name ^String record-id]
  (let [tr (.ensureActive ctx)
        user-ver (.claimLocalVersion ctx)]
    (.mutate tr
             MutationType/SET_VERSIONSTAMPED_KEY
             (.packWithVersionstamp
              (changelog/changelog-subspace store-name)
              (Tuple/from (object-array [(Versionstamp/incomplete user-ver)])))
             (.getBytes record-id))
    (.mutate tr
             MutationType/ADD
             (changelog/sentinel-key store-name)
             (byte-array [1 0 0 0 0 0 0 0]))))
