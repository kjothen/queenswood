(ns com.repldriven.mono.bank-idv.watcher
  (:require
    [com.repldriven.mono.bank-idv.core :as core]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]))

(def ^:private person-identifications-store-name "person-identifications")

;; Reads the PersonIdentification record direct from FDB rather than
;; through bank-party, which already depends on bank-idv; routing via
;; bank-schema is the cycle-free path.
(defn- load-person-identification
  [config party-id]
  (fdb/transact
   config
   (fn [txn]
     (some-> (fdb/load-record
              (fdb/open txn person-identifications-store-name)
              party-id)
             schema/pb->PersonIdentification))
   :idv/load-person-identification
   "Failed to load person-identification for IDV initiation"))

(defn party-changelog-handler
  [config]
  (fn [_ctx changelog-bytes]
    (let [{:keys [organization-id party-id]
           status :status-after}
          (schema/pb->PartyChangelog changelog-bytes)]
      (when (= :party-status-pending status)
        (let-nom>
          [existing (store/get-idv-by-party config party-id)]
          (if existing
            (log/info "IDV already exists for party — skipping"
                      {:party-id party-id
                       :verification-id (:verification-id existing)
                       :status (:status existing)})
            (let-nom>
              [identification (load-person-identification config party-id)
               result (core/initiate
                       config
                       {:organization-id organization-id
                        :party-id party-id
                        :given-name (:given-name identification)
                        :family-name (:family-name identification)
                        :date-of-birth (:date-of-birth identification)})]
              result)))))))
