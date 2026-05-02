(ns com.repldriven.mono.bank-idv.watcher
  "Two changelog handlers wire IDV into the IDV-provider flow.

  - `party-changelog-handler` initiates an IDV record (status
    `:pending`) when a party is created in the pending state.
  - `idv-changelog-handler` publishes a `submit-idv-check`
    command on the message-bus once that IDV record exists. The
    Onfido adapter (or any provider adapter) consumes the command,
    drives the real check, and eventually publishes an
    `idv-completed` event back. The status-update side is handled
    by `bank-idv.events`, not here.

  The party record needed for the submit-idv-check payload is
  loaded directly via `bank-schema/pb->Party` rather than
  `bank-party/get-party` — bank-party already depends on bank-idv
  (its own idv-changelog-handler reads IDV records), so going the
  other way would create a cycle."
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private parties-store-name "parties")

(defn- load-party
  "Reads a Party record direct from FDB via the shared schema
  converter — avoids a brick-level cycle with bank-party."
  [bank organization-id party-id]
  (fdb/transact
   bank
   (fn [txn]
     (when-let [rec (fdb/load-record (fdb/open txn parties-store-name)
                                     organization-id
                                     party-id)]
       (schema/pb->Party rec)))
   :idv/load-party
   "Failed to load party for submit-idv-check"))

(defn party-changelog-handler
  "Returns a watcher handler that initiates IDV when a party
  is created with pending status."
  [{:keys [record-store]}]
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

(defn- publish-submit-idv-check
  "Builds and publishes a `submit-idv-check` command on the bus.
  Loads the IDV (for party-id) and the party (for the applicant's
  name fields). Logs and returns silently on any anomaly — IDVs
  left without a published command remain in `:pending` until
  manual intervention."
  [{:keys [bus schemas record-db record-store command-channel]}
   organization-id verification-id]
  (let [bank {:record-db record-db :record-store record-store}
        idv (store/get-idv bank organization-id verification-id)
        party-id (when-not (error/anomaly? idv) (:party-id idv))
        party (when party-id (load-party bank organization-id party-id))
        schema (get schemas "submit-idv-check")]
    (cond
     (or (nil? bus) (nil? schema) (nil? command-channel))
     (log/info "Skipping submit-idv-check publish — bus/schema/channel"
               "missing (likely brick test or seed-only run)")

     (error/anomaly? idv)
     (log/error "Failed to load IDV for submit-idv-check"
                {:verification-id verification-id :anomaly idv})

     (or (nil? party) (error/anomaly? party))
     (log/error "Failed to load party for submit-idv-check"
                {:party-id party-id :anomaly party})

     :else
     (let [{:keys [given-name family-name date-of-birth]} party
           payload (avro/serialize
                    schema
                    {:organization-id organization-id
                     :verification-id verification-id
                     :party-id party-id
                     :first-name (or given-name "")
                     :last-name (or family-name "")
                     :date-of-birth (when date-of-birth
                                      (str date-of-birth))})]
       (if (error/anomaly? payload)
         (log/error "Failed to serialize submit-idv-check" payload)
         (let [envelope {:command "submit-idv-check"
                         :id (str (utility/uuidv7))
                         :correlation-id (str (utility/uuidv7))
                         :causation-id verification-id
                         :payload payload}]
           (message-bus/send bus command-channel envelope)))))))

(defn idv-changelog-handler
  "Returns a watcher handler that publishes a `submit-idv-check`
  command when an IDV record is created in `:pending` status. The
  Onfido adapter consumes the command and eventually publishes an
  `idv-completed` event back; `bank-idv.events` handles that side."
  [config]
  (fn [_ctx changelog-bytes]
    (let [changelog (schema/pb->IdvChangelog changelog-bytes)
          {:keys [organization-id verification-id]
           status :status-after}
          changelog]
      (when (= :idv-status-pending status)
        (publish-submit-idv-check config organization-id verification-id)))))
