(ns com.repldriven.mono.fdb.core
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.record :as record])
  (:import
    (com.apple.foundationdb.record.provider.foundationdb FDBDatabase)
    (java.util.function Function)))

(defn load-record
  "Loads a record by primary key from the named record store.
  Returns the serialized bytes of the record, or nil if not
  found."
  [^FDBDatabase record-db open-store-fn store-name primary-key]
  (error/try-nom :fdb/load-record
                 {:message "Failed to load record" :store store-name}
                 (.run record-db
                       ^Function
                       (fn [ctx]
                         (record/load
                          (record/open-store open-store-fn ctx store-name)
                          primary-key)))))

(defn transact
  "Opens an FDB Record Layer store and runs f within a single
  transaction. f receives the open FDBRecordStore and should
  return the transaction result.

  The 6-arg form accepts a custom nom category and message for
  call-site-specific anomaly reporting."
  ([^FDBDatabase record-db open-store-fn store-name f]
   (transact record-db
             open-store-fn
             store-name
             f
             :fdb/transact
             "Failed to execute transaction"))
  ([^FDBDatabase record-db open-store-fn store-name f category message]
   (error/try-nom
    category
    message
    (.run record-db
          ^Function
          (fn [ctx] (f (record/open-store open-store-fn ctx store-name)))))))
