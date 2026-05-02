(ns com.repldriven.mono.bank-idv-onfido-simulator.webhook
  "Fires `check.completed` callbacks at every webhook URL the
  tenant has registered. Onfido's real API supports per-account
  webhooks (one or more URLs the platform delivers events to);
  the simulator stores them in `[:webhooks]` on the state atom
  and broadcasts each event to all of them."
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]))

(defn- now-iso8601 [] (str (java.time.Instant/now)))

(defn- fire-one
  [url body]
  (let [res (http/request {:method :post
                           :url url
                           :headers {"Content-Type" "application/json"}
                           :body body})]
    (when (or (error/anomaly? res)
              (and (:status res) (>= (:status res) 400)))
      (log/error "Onfido webhook delivery failed to" url ":" res))
    res))

(defn fire-check-completed
  "Broadcasts a `check.completed` event for `check-id` with
  `result` (`\"clear\"` or `\"consider\"`) to every registered
  webhook URL on `state`. `external-id` is the simulator-only
  correlation field that the adapter uses to map the webhook
  back to the originating `:verification-id`."
  [state check-id result external-id]
  (let [urls (mapv :url (:webhooks @state))
        object (cond-> {:id check-id
                        :status "complete"
                        :result result
                        :completed_at_iso8601 (now-iso8601)}

                       external-id
                       (assoc :external_id external-id))
        body (json/write-str
              {:payload {:resource_type "check"
                         :action "check.completed"
                         :object object}})]
    (if (empty? urls)
      (log/warn "No Onfido webhooks registered; skipping check.completed for"
                check-id)
      (doseq [url urls]
        (fire-one url body)))))
