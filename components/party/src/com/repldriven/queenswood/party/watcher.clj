(ns com.repldriven.queenswood.party.watcher
  (:require
    [com.repldriven.queenswood.party.domain :as domain]
    [com.repldriven.queenswood.party.store :as store]

    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.queenswood.party-query.interface :as q]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(def ^:private idv-status->party-transition
  {:idv-status-accepted domain/activate-party
   :idv-status-rejected domain/reject-party})

(defn idv-changelog-handler
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [bank-id party-id] status :status-after}
          changelog
          transition (idv-status->party-transition status)]
      (when transition
        (let-nom> [txn (fdb/ctx->txn ctx record-store)
                   party (q/get-party txn bank-id party-id)]
          (when (= :party-status-pending (:status party))
            (let [updated-party (transition party)]
              (store/save-party txn
                                updated-party
                                {:bank-id bank-id
                                 :party-id party-id
                                 :status-before (:status party)
                                 :status-after (:status
                                                updated-party)}))))))))
