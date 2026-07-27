(ns com.repldriven.queenswood.cash-account.events
  (:require
    [com.repldriven.queenswood.cash-account.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- handle-status-changed
  [config data]
  (let [{:keys [record-db record-store]} config
        bank {:record-db record-db :record-store record-store}
        {:keys [bank-id account-id status-after]} data]
    (core/complete-status-transition bank
                                     bank-id
                                     account-id
                                     (keyword status-after))))

(defn- dispatch
  [config message]
  (let [{:keys [event payload]} message
        {:keys [schemas]} config
        schema (get schemas event)]
    (if-not schema
      (do (log/warnf "Unknown cash-account event: %s" event) nil)
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case event
          "cash-account-status-changed" (handle-status-changed config data)
          (do (log/warnf "Unknown cash-account event: %s" event) nil))))))

(defrecord CashAccountEventProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
