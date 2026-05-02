(ns com.repldriven.mono.bank-idv-onfido-adapter.commands
  (:require
    [com.repldriven.mono.bank-idv-onfido-adapter.onfido :as onfido]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.processor.interface :as processor]))

(def ^:private command-handlers
  {"submit-idv-check" (fn [config data] (onfido/submit-idv-check config data))})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (do (log/warnf "Onfido adapter ignoring unknown command: %s" command)
          nil)
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (do (log/warnf "No schema found for command: %s" command)
              nil)
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord OnfidoCommandProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
