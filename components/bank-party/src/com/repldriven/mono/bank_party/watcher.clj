(ns com.repldriven.mono.bank-party.watcher
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.bank-idv.interface :as idv]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions parties from
  pending to active when their IDV is accepted."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id status-after]} changelog]
      (when (= :idv-status-accepted status-after)
        (let [txn (fdb/ctx->txn ctx record-store)
              idv-record (idv/find-idv txn organization-id verification-id)]
          (when idv-record
            (let [party-id (:party-id idv-record)
                  party (store/find-party txn organization-id party-id)]
              (when (and party (= :party-status-pending (:status party)))
                (let [activated (domain/activate-party party)]
                  (store/save-party txn
                                    activated
                                    {:organization-id organization-id
                                     :party-id party-id
                                     :status-before :party-status-pending
                                     :status-after
                                     :party-status-active}))))))))))
