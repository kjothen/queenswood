(ns com.repldriven.mono.bank-interest.commands
  (:require
    [com.repldriven.mono.bank-interest.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error
     :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      (let-nom> [payload (avro/serialize (schemas "interest-result")
                                         result)]
        {:status "ACCEPTED" :payload payload}))))

(def ^:private command-handlers
  "Map of command-name → `(fn [config data] response)`. Lifting the
  registry out of the dispatcher's `case` lets unknown-command
  rejection fire BEFORE schema lookup or Avro deserialization, so
  a pure unit test can exercise the rejection path without
  spinning up the system."
  {"accrue-daily-interest"
   (fn [config data] (->response config (core/accrue-daily config data)))
   "capitalize-monthly-interest"
   (fn [config data]
     (->response config (core/capitalize-monthly config data)))})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :interest/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :interest/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord InterestProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
