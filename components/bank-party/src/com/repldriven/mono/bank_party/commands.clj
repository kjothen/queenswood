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

(def ^:private command-handlers
  "Map of command-name → `(fn [config data] response)`. Lifting the
  registry out of the dispatcher's `case` lets unknown-command
  rejection fire BEFORE schema lookup or Avro deserialization, so
  a pure unit test can exercise the rejection path without
  spinning up the system."
  {"create-party" (fn [config data]
                    (->response config (core/new-party config data)))})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :party/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :party/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord PartyProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
