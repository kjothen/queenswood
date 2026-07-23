(ns com.repldriven.queenswood.idv.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.queenswood.idv.domain :as domain]
    [com.repldriven.queenswood.idv.store :as store]

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

(defn get
  [txn data]
  (get-idv txn (:bank-id data) (:verification-id data)))
