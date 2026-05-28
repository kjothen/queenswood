(ns com.repldriven.mono.bank-idv.events
  (:require
    [com.repldriven.mono.bank-idv.core :as core]
    [com.repldriven.mono.bank-idv.domain :as domain]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->updated
  [idv status]
  (case status
    "ACCEPTED" (domain/accepted-idv idv)
    "REJECTED" (domain/rejected-idv idv)
    nil))

(defn- handle-idv-completed
  [config data]
  (let [{:keys [record-db record-store]} config
        bank {:record-db record-db :record-store record-store}
        {:keys [bank-id verification-id status]} data
        idv (core/get-idv bank bank-id verification-id)]
    (cond
     (error/anomaly? idv)
     (do (log/error "Failed to load IDV for idv-completed event"
                    {:verification-id verification-id :anomaly idv})
         nil)

     :else
     (let [updated (->updated idv status)]
       (if-not updated
         (do (log/warnf "Unknown idv-completed status: %s" status)
             nil)
         (let-nom>
           [_ (core/save-idv bank
                             updated
                             {:bank-id bank-id
                              :verification-id verification-id
                              :status-before (:status idv)
                              :status-after (:status updated)})]
           updated))))))

(defn- dispatch
  [config message]
  (let [{:keys [event payload]} message
        {:keys [schemas]} config
        schema (get schemas event)]
    (if-not schema
      (do (log/warnf "Unknown IDV event: %s" event) nil)
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case event
          "idv-completed" (handle-idv-completed config data)
          (do (log/warnf "Unknown IDV event: %s" event) nil))))))

(defrecord IdvEventProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
