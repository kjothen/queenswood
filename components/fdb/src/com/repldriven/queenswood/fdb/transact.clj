(ns com.repldriven.queenswood.fdb.transact
  (:require
    [com.repldriven.mono.error.interface :as error :refer [try-nom]])
  (:import
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase)
    (java.util.function Function)))

(defn- open-store
  [open-store-fn ctx store-name]
  (open-store-fn ctx store-name))

(defrecord Txn [open prefix])

(defn open
  [^Txn txn store-name]
  ((:open txn) store-name))

(defn transact
  ([txn-or-config f]
   (transact txn-or-config f :fdb/transact "Failed to execute transaction"))

  ([txn-or-config f category message]
   (if (instance? Txn txn-or-config)
     (try-nom category message (f txn-or-config))
     (let [{:keys [record-db record-store]} txn-or-config
           keyspace-prefix (or (:keyspace-prefix txn-or-config)
                               (:keyspace-prefix (meta record-store)))]
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
                                       (f (->Txn open-fn keyspace-prefix)))]
                   (if (error/anomaly? result)
                     ;; nosemgrep: no-raw-throw
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
