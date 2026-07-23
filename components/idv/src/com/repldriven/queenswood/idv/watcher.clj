(ns com.repldriven.queenswood.idv.watcher
  (:require
    [com.repldriven.queenswood.idv.core :as core]
    [com.repldriven.queenswood.idv.store :as store]

    [com.repldriven.queenswood.person-identification.interface :as
     person-identification]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]))

(defn party-changelog-handler
  [config]
  (fn [_ctx changelog-bytes]
    (let [{:keys [bank-id party-id]
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
              [identification (person-identification/get-person-identification
                               config
                               party-id)
               result (core/initiate
                       config
                       {:bank-id bank-id
                        :party-id party-id
                        :given-name (:given-name identification)
                        :middle-names (:middle-names identification)
                        :family-name (:family-name identification)
                        :date-of-birth (:date-of-birth identification)
                        :address (:address identification)})]
              result)))))))
