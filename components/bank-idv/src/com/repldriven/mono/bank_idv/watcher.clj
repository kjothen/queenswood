(ns com.repldriven.mono.bank-idv.watcher
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]))

(defn party-changelog-handler
  "Returns a watcher handler that initiates IDV when a party
  is created with pending status."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->PartyChangelog changelog-bytes)
          {:keys [organization-id party-id] status :status-after}
          changelog]
      (when (= :party-status-pending status)
        (let-nom> [txn (fdb/ctx->txn ctx record-store)
                   idv (domain/new-idv {:organization-id organization-id
                                        :party-id party-id})
                   {:keys [verification-id]} idv]
          (store/save-idv txn
                          idv
                          {:organization-id organization-id
                           :verification-id verification-id
                           :status-after (:status idv)}))))))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions a pending IDV
  to accepted."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id] status :status-after}
          changelog]
      (when (= :idv-status-pending status)
        (let-nom> [txn (fdb/ctx->txn ctx record-store)
                   idv (store/get-idv txn organization-id verification-id)
                   accepted (domain/accepted-idv idv)]
          (store/save-idv txn
                          accepted
                          {:organization-id organization-id
                           :verification-id verification-id
                           :status-before status
                           :status-after (:status accepted)}))))))
