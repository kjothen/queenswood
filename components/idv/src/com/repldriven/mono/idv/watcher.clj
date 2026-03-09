(ns com.repldriven.mono.idv.watcher
  (:require
    [com.repldriven.mono.idv.domain :as domain]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn make-handler
  "Returns a watcher handler that transitions a pending IDV
  to accepted. Captures record-store to open the idvs store
  within the same transaction."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)]
      (when (= :pending (:status-after changelog))
        (let [store (record-store ctx "idvs")
              record (fdb/load-record store
                                      (:verification-id changelog))]
          (when record
            (let [idv (schema/pb->Idv record)
                  accepted (domain/accept-idv idv)]
              (fdb/save-record store (schema/Idv->java accepted))
              (fdb/write-changelog
               store
               "idvs"
               (:verification-id accepted)
               (schema/IdvChangelog->pb
                {:verification-id (:verification-id accepted)
                 :status-before :pending
                 :status-after :accepted})))))))))
