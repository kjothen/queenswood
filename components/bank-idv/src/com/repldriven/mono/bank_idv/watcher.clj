(ns com.repldriven.mono.bank-idv.watcher
  (:require
    [com.repldriven.mono.bank-idv.core :as core]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.bank-person-identification.interface :as person-id]
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]))

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
              [identification (person-id/get-person-identification
                               config
                               party-id)
               result (core/initiate
                       config
                       {:organization-id organization-id
                        :party-id party-id
                        :given-name (:given-name identification)
                        :family-name (:family-name identification)
                        :date-of-birth (:date-of-birth identification)})]
              result)))))))
