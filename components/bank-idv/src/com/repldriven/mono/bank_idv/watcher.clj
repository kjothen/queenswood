(ns com.repldriven.mono.bank-idv.watcher
  "One changelog handler that initiates IDV when a person party
  enters `:party-status-pending`.

  The handler is idempotent: it consults the unique
  `Idv_by_party` index before writing, so changelog replay or a
  duplicate event won't create a second IDV. If no IDV exists
  for the party, it delegates to `core/initiate` which writes
  the IDV record and publishes a `submit-idv-check` command on
  the IDV-provider channel — the same code path the
  `initiate-idv` command takes.

  Names ride into the IDV envelope from `PersonIdentification`
  (loaded directly from FDB inside the same transaction), since
  the `Party` proto only carries `display_name`."
  (:require
    [com.repldriven.mono.bank-idv.core :as core]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]))

(def ^:private person-identifications-store-name "person-identifications")

(defn- load-person-identification
  "Reads the PersonIdentification record for a party direct from
  FDB. Returns the map, nil if no record, or anomaly. Bypasses
  bank-party because bank-party already depends on bank-idv;
  the seam through `bank-schema/pb->PersonIdentification` is
  the cycle-free path."
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
  "Returns a watcher handler that idempotently initiates IDV
  when a person party enters pending state. Only person parties
  reach `:party-status-pending` (internal/organization parties
  start active), so no explicit type check is needed."
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
