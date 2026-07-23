(ns com.repldriven.queenswood.party.commands
  (:require
    [com.repldriven.queenswood.party.core :as core]

    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "party") (schema/pb->Party result))})))

(def ^:private command-handlers
  {"create-party" (fn [config data]
                    (->response config (core/new-party config data)))
   "merge-party" (fn [config data]
                   (->response config (core/merge-party config data)))})

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
