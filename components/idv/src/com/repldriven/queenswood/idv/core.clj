(ns com.repldriven.queenswood.idv.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.queenswood.idv.domain :as domain]
    [com.repldriven.queenswood.idv.store :as store]

    [com.repldriven.queenswood.person-identification.interface :as
     person-identification]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- publish-submit-idv-check
  [config idv data]
  (let [{:keys [bus schemas idv-command-channel]} config
        {:keys [bank-id verification-id party-id]} idv
        {:keys [given-name middle-names family-name date-of-birth address]} data
        schema (clojure.core/get schemas "submit-idv-check")]
    (when (and bus schema idv-command-channel)
      (let [payload (avro/serialize
                     schema
                     {:bank-id bank-id
                      :verification-id verification-id
                      :party-id party-id
                      :first-name (or given-name "")
                      :middle-names middle-names
                      :last-name (or family-name "")
                      :date-of-birth (when date-of-birth
                                       (str date-of-birth))
                      :address address})]
        (if (error/anomaly? payload)
          (log/error "Failed to serialize submit-idv-check" payload)
          (let [envelope {:command "submit-idv-check"
                          :id (str (utility/uuidv7))
                          :correlation-id (str (utility/uuidv7))
                          :causation-id verification-id
                          :payload payload}]
            (message-bus/send bus idv-command-channel envelope)))))))

(defn save-idv
  "Save an IDV, converting a uniqueness-violation result into an
  `:idv/already-exists` rejection."
  [txn idv changelog]
  (let [result (store/save-idv txn idv changelog)]
    (if (store/uniqueness-violation? result)
      (error/reject :idv/already-exists
                    {:message "IDV already exists for party"
                     :party-id (:party-id idv)})
      result)))

(defn get-idv
  "Load an IDV by composite primary key, rejecting with
  `:idv/not-found` if the record is missing."
  [txn bank-id verification-id]
  (let-nom> [idv (store/get-idv txn bank-id verification-id)]
    (or idv
        (error/reject :idv/not-found
                      {:message "IDV not found"
                       :bank-id bank-id
                       :verification-id verification-id}))))

(defn initiate
  [config data]
  (let-nom>
    [idv (domain/new-idv data)
     saved (save-idv config
                     idv
                     {:verification-id (:verification-id idv)
                      :status-after (:status idv)})]
    (publish-submit-idv-check config saved data)
    saved))

(defn initiate-for-party
  "Open an IDV for a party that has just entered pending, reading the
  party's identification to seed the check.

  Gated on no IDV already existing for the party, and skips silently
  when one does. Event redelivery and replay must be a no-op here, not
  a rejection — a party arriving pending twice is the normal case."
  [config bank-id party-id]
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
         result (initiate config
                          {:bank-id bank-id
                           :party-id party-id
                           :given-name (:given-name identification)
                           :middle-names (:middle-names identification)
                           :family-name (:family-name identification)
                           :date-of-birth (:date-of-birth identification)
                           :address (:address identification)})]
        result))))

(defn get
  [txn data]
  (get-idv txn (:bank-id data) (:verification-id data)))
