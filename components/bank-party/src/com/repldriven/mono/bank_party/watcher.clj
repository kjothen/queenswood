(ns com.repldriven.mono.bank-party.watcher
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.bank-idv.interface :as idv]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions parties from
  pending to active when their IDV is accepted."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id] status :status-after}
          changelog]
      (when (= :idv-status-accepted status)
        (let-nom> [txn (fdb/ctx->txn ctx record-store)
                   idv (idv/get-idv txn organization-id verification-id)
                   {:keys [party-id]} idv
                   party (store/get-party txn organization-id party-id)]
          (when (= :party-status-pending (:status party))
            (let [activated-party (domain/activate-party party)]
              (store/save-party txn
                                activated-party
                                {:organization-id organization-id
                                 :party-id party-id
                                 :status-before (:status party)
                                 :status-after (:status
                                                activated-party)}))))))))

