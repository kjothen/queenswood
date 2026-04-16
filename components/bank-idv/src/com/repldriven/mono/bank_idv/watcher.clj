(ns com.repldriven.mono.bank-idv.watcher
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn party-changelog-handler
  "Returns a watcher handler that initiates IDV when a party
  is created with pending status."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->PartyChangelog changelog-bytes)
          {:keys [organization-id party-id status-after]} changelog]
      (when (= :party-status-pending status-after)
        (let [txn (fdb/ctx->txn ctx record-store)
              idv (domain/new-idv {:organization-id organization-id
                                   :party-id party-id})
              {:keys [verification-id status]} idv]
          (store/save-idv txn
                          idv
                          {:verification-id verification-id
                           :status-after status}))))))

(defn idv-changelog-handler
  "Returns a watcher handler that transitions a pending IDV
  to accepted."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id status-after]} changelog]
      (when (= :idv-status-pending status-after)
        (let [txn (fdb/ctx->txn ctx record-store)
              idv (store/find-idv txn organization-id verification-id)]
          (when idv
            (let [accepted (domain/accepted-idv idv)]
              (store/save-idv txn
                              accepted
                              {:organization-id organization-id
                               :verification-id verification-id
                               :status-before status-after
                               :status-after (:status accepted)}))))))))
