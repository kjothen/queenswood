(ns com.repldriven.queenswood.uk-companies-house-adapter.commands
  (:require
    [com.repldriven.queenswood.company-registry.interface :as company-registry]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.processor.interface :as processor]))

(defn- ->response
  [config result]
  (if (error/anomaly? result)
    result
    (let [{:keys [schemas]} config]
      {:status "ACCEPTED"
       :payload (avro/serialize (schemas "company") result)})))

(defn- lookup-company
  "Fetch the company profile from the registry of record and cache it
  in FDB. Unlike the Onfido and ClearBank adapters this replies with
  the record rather than persisting an intent: the caller is a live
  request waiting on the command response, so there is nothing to
  drain later. The resolved registry rides back on the reply so the
  caller need not know which one a blank id defaults to."
  [config data]
  (let [{:keys [companies-house-url record-db record-store]} config
        {:keys [registry-id company-number]} data
        registry-id (or registry-id company-registry/default-registry)
        result (company-registry/lookup-company
                {:companies-house-url companies-house-url
                 :record-db record-db
                 :record-store record-store}
                registry-id
                company-number)]
    (->response config
                (if (error/anomaly? result)
                  result
                  (assoc result :registry-id registry-id)))))

(def ^:private command-handlers {"lookup-company" lookup-company})

(defn- dispatch
  [config message]
  (let [{:keys [command payload]} message
        handler (get command-handlers command)]
    (if (nil? handler)
      (error/reject :uk-companies-house-adapter/unknown-command
                    (str "Unknown command: " command))
      (let [{:keys [schemas]} config
            schema (get schemas command)]
        (if-not schema
          (error/fail :uk-companies-house-adapter/process-command
                      {:message "No schema found for command"
                       :command command})
          (let-nom> [data (avro/deserialize-same schema payload)]
            (handler config data)))))))

(defrecord UkCompaniesHouseCommandProcessor [config]
  processor/Processor
    (process [_ message] (dispatch config message)))
