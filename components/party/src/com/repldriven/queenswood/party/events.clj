(ns com.repldriven.queenswood.party.events
  (:require
    [com.repldriven.queenswood.party.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- handle-idv-status-changed
  [config data]
  (let [{:keys [bank-id party-id status-after]} data]
    (core/apply-idv-status config bank-id party-id status-after)))

(defn- dispatch
  [config message]
  (let [{:keys [event payload]} message
        {:keys [schemas]} config
        schema (get schemas event)]
    (if-not schema
      (do (log/warnf "Unknown idvs event: %s" event) nil)
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case event
          "idv-status-changed" (handle-idv-status-changed config data)
          (do (log/warnf "Unknown idvs event: %s" event) nil))))))

(defrecord PartyIdvEventProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
