(ns com.repldriven.queenswood.clearbank-adapter.commands
  (:require
    [com.repldriven.queenswood.clearbank-adapter.clearbank :as clearbank]

    [com.repldriven.queenswood.clearbank-relay.interface :as relay]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- submit-payment-intent
  [config data]
  (let [{:keys [end-to-end-id]} data
        res (relay/save-intent (select-keys config [:record-store :record-db])
                               {:intent-id (str (utility/uuidv7))
                                :dedup-key end-to-end-id
                                :request (clearbank/->fps-body data)
                                :status "pending"
                                :attempts 0
                                :created-at (utility/now)})]
    (cond
     (not (error/anomaly? res))
     {:status "ACCEPTED"}

     (relay/uniqueness-violation? res)
     {:status "ACCEPTED"}

     :else
     res)))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (do (log/warnf "No schema found for command: %s" command)
          nil)
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "submit-payment"
          (submit-payment-intent config data)

          (do (log/warnf "Unknown command: %s" command)
              nil))))))

(defrecord ClearBankCommandProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
