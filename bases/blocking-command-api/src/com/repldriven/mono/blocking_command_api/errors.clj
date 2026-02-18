(ns com.repldriven.mono.blocking-command-api.errors
  (:require
    [com.repldriven.mono.command.interface :as command]))

(defn- request->ids
  "Extract [idempotency-key correlation-id] from a request map's headers."
  [req]
  (let [idempotency-key (get-in req [:headers "idempotency-key"])
        correlation-id (or (get-in req [:headers "correlation-id"])
                           idempotency-key)]
    [idempotency-key correlation-id]))

(defn request->command-error-response
  "Build a command-response error body from a request map."
  [req category details]
  (let [[idempotency-key correlation-id] (request->ids req)]
    (command/->command-error-response idempotency-key
                                      correlation-id
                                      category
                                      details)))

(defn coercion-ex->command-response
  "Convert a Reitit coercion exception and request to a command-response error body."
  [req category ex]
  (request->command-error-response req
                                   category
                                   (select-keys (ex-data ex) [:humanized :in])))
