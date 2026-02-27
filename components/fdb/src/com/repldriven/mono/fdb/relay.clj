(ns com.repldriven.mono.fdb.relay
  (:require
    [clojure.core.async :refer [alts!! chan thread >!!]]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.outbox :as outbox])
  (:import
    (com.apple.foundationdb KeySelector Range StreamingMode)
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase
                                                         FDBStoreTimer$Waits)
    (com.apple.foundationdb.tuple ByteArrayUtil)))

(defn- read-checkpoint
  "Returns the raw FDB key bytes of the last processed outbox entry stored
  at the checkpoint key, or nil when no checkpoint exists yet."
  [ctx store-name]
  (.asyncToSync ctx
                FDBStoreTimer$Waits/WAIT_LOAD_RECORD
                (.get (.ensureActive ctx) (outbox/checkpoint-key store-name))))

(defn- scan-entries
  "Returns a Java List of KeyValues in the outbox for store-name that come
  strictly after from-key (raw key bytes), or all entries when from-key is
  nil. Reads at most limit entries."
  [ctx store-name from-key limit]
  (let [tr (.ensureActive ctx)
        subspace (outbox/outbox-subspace store-name)
        begin (if from-key
                (KeySelector/firstGreaterThan from-key)
                (KeySelector/firstGreaterOrEqual (.pack subspace)))
        end (KeySelector/firstGreaterOrEqual (ByteArrayUtil/strinc (.pack
                                                                    subspace)))]
    (.asyncToSync ctx
                  FDBStoreTimer$Waits/WAIT_SCAN_RECORDS
                  (->
                    (.getRange tr begin end limit false StreamingMode/ITERATOR)
                    .asList))))

(defn relay-batch
  "Reads the outbox for store-name, calls (handler-fn key-bytes val-bytes)
  for each new entry, advances the checkpoint to the last processed key,
  and clears the processed range. Returns nil (or an anomaly on failure)."
  [^FDBDatabase record-db store-name handler-fn]
  (error/try-nom
   :fdb/relay-batch
   {:message "Failed to relay batch" :store store-name}
   (.run record-db
         (reify
          java.util.function.Function
            (apply [_ ctx]
              (let [tr (.ensureActive ctx)
                    checkpoint (read-checkpoint ctx store-name)
                    entries (scan-entries ctx store-name checkpoint 100)]
                (when (seq entries)
                  (doseq [kv entries] (handler-fn (.getKey kv) (.getValue kv)))
                  (let [last-key (.getKey (last entries))]
                    (.set tr (outbox/checkpoint-key store-name) last-key)
                    (.clear tr
                            (Range. (.pack (outbox/outbox-subspace store-name))
                                    (ByteArrayUtil/strinc last-key)))))
                nil))))))

(defn- watch-chan
  "Sets up an FDB watch on the outbox sentinel for store-name and returns a
  core.async channel that receives nil when the watch fires."
  [^FDBDatabase record-db store-name]
  (let [ch (chan 1)
        future (error/try-nom
                :fdb/relay-watch
                {:message "Failed to set up relay watch" :store store-name}
                (.run record-db
                      (reify
                       java.util.function.Function
                         (apply [_ ctx]
                           (.watch (.ensureActive ctx)
                                   (outbox/sentinel-key store-name))))))]
    (.thenAccept future
                 (reify
                  java.util.function.Consumer
                    (accept [_ _] (>!! ch nil))))
    ch))

(defn start-relay
  "Starts a relay loop that calls (handler-fn key-bytes val-bytes) for each
  new outbox entry. Processes any backlog immediately, then watches the
  sentinel and processes each new batch as it arrives.

  Returns {:stop stop-ch}. Put any value to stop-ch to stop the relay."
  [^FDBDatabase record-db store-name handler-fn]
  (let [stop-ch (chan 1)]
    (thread (relay-batch record-db store-name handler-fn)
            (loop []
              (let [watch (watch-chan record-db store-name)
                    [_ fired-ch] (alts!! [stop-ch watch] :priority true)]
                (when (not= fired-ch stop-ch)
                  (relay-batch record-db store-name handler-fn)
                  (recur)))))
    {:stop stop-ch}))
