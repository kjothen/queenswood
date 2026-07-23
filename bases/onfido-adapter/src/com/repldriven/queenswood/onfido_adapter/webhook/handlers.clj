(ns com.repldriven.queenswood.onfido-adapter.webhook.handlers
  (:require
    [com.repldriven.queenswood.onfido-adapter.publisher :as publisher]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.queenswood.onfido-relay.interface :as relay]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- record-event
  "Serialise the event descriptor and write it to the outbox in one FDB
  transaction. A duplicate `dedup-key` (redelivered webhook) counts as
  success. Returns `:ok` or a non-dedup anomaly."
  [request {:keys [event-name dedup-key data]}]
  (let [{:keys [record-db record-store avro]} request
        schema (get avro event-name)]
    (if (nil? schema)
      (error/fail :onfido-adapter/unknown-event
                  {:message "No schema for event" :event event-name})
      (let-nom> [payload (avro/serialize schema data)]
        (let [res (relay/save-event
                   {:record-db record-db :record-store record-store}
                   {:outbox-id (str (utility/uuidv7))
                    :dedup-key dedup-key
                    :event-name event-name
                    :payload payload
                    :correlation-id (str (utility/uuidv7))
                    :causation-id (str (utility/uuidv7))
                    :created-at (utility/now)})]
          (if (relay/uniqueness-violation? res)
            :ok
            res))))))

(defn check-completed
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [payload]} body
          {:keys [object]} payload
          {:keys [id result external_id]} object]
      (log/info "Onfido check.completed webhook received"
                {:check-id id
                 :result result
                 :external-id external_id})
      (let [res (record-event request (publisher/->idv-completed payload))]
        (if (error/anomaly? res)
          (do (log/error "Failed to record idv-completed webhook" res)
              {:status 500 :body {:error "webhook not recorded"}})
          {:status 200 :body {:received true}})))))
