(ns com.repldriven.mono.bank-party.commands
  (:require
    [com.repldriven.mono.bank-party.core :as core]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- ->response
  "Converts a protobuf record to an ACCEPTED response.
  Returns anomalies unchanged for the processor to handle."
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "party") (schema/pb->Party result))})))

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        {:keys [schemas]} config
        schema (get schemas command)]
    (if-not schema
      (error/fail :party/process-command
                  {:message "No schema found for command" :command command})
      (let-nom> [data (avro/deserialize-same schema payload)]
        (case command
          "create-party" (->response config (core/new-party config data))
          (error/reject :party/unknown-command
                        (str "Unknown command: " command)))))))

(defrecord PartyProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
