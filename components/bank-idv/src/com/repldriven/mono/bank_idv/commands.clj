(ns com.repldriven.mono.bank-idv.commands
  (:require
    [com.repldriven.mono.bank-idv.core :as core]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- idv->avro
  "Normalizes the IDV map for Avro: :completed-at is
  nullable in the Avro schema, so proto's 0 default (or
  an unset key) becomes nil."
  [idv]
  (update idv :completed-at (fn [t] (when (and t (pos? t)) t))))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "idv") (idv->avro result))})))

(def ^:private command-handlers
  "Map of command-name → `(fn [config data] response)`. Lifting the
  registry out of the dispatcher's `case` lets unknown-command
  rejection fire BEFORE schema lookup or Avro deserialization, so
  a pure unit test can exercise the rejection path without
  spinning up the system."
  {"initiate-idv" (fn [config data]
                    (->response config (core/initiate config data)))
   "get-idv" (fn [config data] (->response config (core/get config data)))})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :idv/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :idv/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord IdvProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
