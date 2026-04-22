(ns com.repldriven.mono.bank-api.commands
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]

    [clojure.string :as str]))

(defn- reason->kind
  "Parse the stringified keyword reason emitted by a command
  processor back to a qualified keyword (e.g. \":x/y\" -> :x/y)."
  [reason]
  (when (and (string? reason) (str/starts-with? reason ":"))
    (keyword (subs reason 1))))

(defn- rejected-response
  "Build a ring response for a REJECTED command response. Maps the
  rejection reason to an appropriate status via `rejection-kind->status`
  (falls back to 422 when the reason is absent or unparseable)."
  [result]
  (let [status (or (some-> (:reason result)
                           reason->kind
                           errors/rejection-kind->status)
                   422)]
    {:status status
     :body (errors/error-response status "REJECTED" result)}))

(defn- get-schema
  [schemas command-name]
  (or (get schemas command-name)
      (error/fail :api/unknown-command
                  {:message "Unknown command" :command command-name})))

(defn- decode-payload
  [schemas response-schema result]
  (if-let [payload (:payload result)]
    (let [schema (get schemas response-schema)]
      (avro/deserialize-same schema payload))
    {}))

(defn send
  [dispatcher request command-name response-schema data]
  (let [schemas (:avro request)
        envelope (command/req->command-request request command-name)
        result (let-nom>
                 [schema (get-schema schemas command-name)
                  payload
                  (avro/serialize schema data)]
                 (command/send dispatcher (assoc envelope :payload payload)))]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          (= "REJECTED" (:status result))
          (rejected-response result)
          (= "FAILED" (:status result))
          {:status 500 :body (errors/error-response 500 "FAILED" result)}
          :else
          (let [body (decode-payload schemas response-schema result)]
            (if (error/anomaly? body)
              (errors/anomaly->response body)
              {:status 200 :body body})))))
