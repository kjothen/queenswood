(ns com.repldriven.mono.bank-clearbank-adapter.commands
  (:require
    [com.repldriven.mono.bank-clearbank-adapter.clearbank :as clearbank]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.bank-clearbank-relay.interface :as relay]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- submit-payment-intent
  "Persist the outbound FPS call as a pending intent in one FDB
  transaction, then ack. The out-of-transaction outbound relay makes the
  actual ClearBank call. A redelivered command (same end-to-end id)
  dedupes at the unique index and is treated as accepted."
  [config data]
  (let [{:keys [record-db record-store]} config
        fdb-config {:record-db record-db :record-store record-store}
        {:keys [end-to-end-id]} data
        res (relay/save-intent fdb-config
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
      (do (log/warnf "No schema found for command: %s"
                     command)
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
