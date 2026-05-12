(ns com.repldriven.mono.bank-party.watcher
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.bank-idv.interface :as idv]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private idv-status->party-transition
  {:idv-status-accepted domain/activate-party
   :idv-status-rejected domain/reject-party})

(defn idv-changelog-handler
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id] status :status-after}
          changelog
          transition (idv-status->party-transition status)]
      (when transition
        (let-nom> [txn (fdb/ctx->txn ctx record-store)
                   idv (idv/get-idv txn organization-id verification-id)
                   {:keys [party-id]} idv
                   party (store/get-party txn organization-id party-id)]
          (when (= :party-status-pending (:status party))
            (let [updated-party (transition party)]
              (store/save-party txn
                                updated-party
                                {:organization-id organization-id
                                 :party-id party-id
                                 :status-before (:status party)
                                 :status-after (:status
                                                updated-party)}))))))))
