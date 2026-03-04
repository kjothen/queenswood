(ns com.repldriven.mono.bank-api.commands
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]))

(defn- get-schema
  [schemas command-name]
  (or (get schemas command-name)
      (error/fail :bank-api/unknown-command
                  {:message "Unknown command" :command command-name})))

(defn- decode-payload
  [schemas response-schema result]
  (if-let [payload (:payload result)]
    (let [schema (get schemas response-schema)
          decoded (avro/deserialize-same schema payload)]
      (if (error/anomaly? decoded) decoded (assoc result :payload decoded)))
    result))

(defn send
  [request command-name response-schema data]
  (let [dispatcher (:command-dispatcher request)
        schemas (:avro request)
        envelope (command/req->command-request request command-name)
        result (error/let-nom> [schema (get-schema schemas command-name)
                                payload (avro/serialize schema data)]
                 (command/send dispatcher (assoc envelope :payload payload)))]
    (cond
     (error/anomaly? result)
     {:status (if (= (error/kind result) :command/timeout)
                408
                500)
      :body (command/command-response envelope result)}

     (= "REJECTED" (:status result))
     {:status 422 :body result}

     :else
     {:status 200
      :body (decode-payload schemas
                            response-schema
                            result)})))
