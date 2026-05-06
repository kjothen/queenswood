(ns com.repldriven.mono.bank-idv.core
  (:refer-clojure :exclude [get])
  (:require
    [com.repldriven.mono.bank-idv.domain :as domain]
    [com.repldriven.mono.bank-idv.store :as store]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- publish-submit-idv-check
  [config idv data]
  (let [{:keys [bus schemas idv-command-channel]} config
        {:keys [organization-id verification-id party-id]} idv
        {:keys [given-name family-name date-of-birth]} data
        schema (clojure.core/get schemas "submit-idv-check")]
    (when (and bus schema idv-command-channel)
      (let [payload (avro/serialize
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
            (message-bus/send bus idv-command-channel envelope)))))))

(defn initiate
  [config data]
  (let-nom>
    [idv (domain/new-idv data)
     saved (store/save-idv config
                           idv
                           {:verification-id (:verification-id idv)
                            :status-after (:status idv)})]
    (publish-submit-idv-check config saved data)
    saved))

(defn get
  [txn data]
  (let [{:keys [organization-id verification-id]} data]
    (store/get-idv txn organization-id verification-id)))
