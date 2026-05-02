(ns com.repldriven.mono.bank-idv-onfido-adapter.webhook.handlers
  (:require
    [com.repldriven.mono.bank-idv-onfido-adapter.publisher :as publisher]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.log.interface :as log]))

(defn- request-config
  [request]
  (select-keys request [:bus :avro :event-channel]))

(defn check-completed
  [_config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [payload]} body
          {:keys [object]} payload
          {:keys [id result external_id]} object
          config (request-config request)]
      (log/info "Onfido check.completed webhook received"
                {:check-id id
                 :result result
                 :external-id external_id})
      (let [res (publisher/publish-idv-completed config payload)]
        (when (error/anomaly? res)
          (log/error "Failed to publish idv-completed event" res)))
      {:status 200 :body {:received true}})))
