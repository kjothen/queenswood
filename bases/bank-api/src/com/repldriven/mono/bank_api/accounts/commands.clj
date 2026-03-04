(ns com.repldriven.mono.bank-api.accounts.commands
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private response-schema
  {"open-account" "account" "close-account" "account"})

(defn- decode-payload
  [schemas command-name result]
  (if-let [payload (:payload result)]
    (let [schema (get schemas (get response-schema command-name))
          decoded (avro/deserialize-same schema payload)]
      (if (error/anomaly? decoded) decoded (assoc result :payload decoded)))
    result))

(defn- send-command
  [request command-name data]
  (let [dispatcher (:command-dispatcher request)
        schemas (:avro request)
        schema (get schemas command-name)]
    (if-not schema
      {:status 400
       :body (command/req->command-response
              request
              (error/fail :bank-api/unknown-command
                          {:message "No Avro schema for command"
                           :command command-name}))}
      (let [result (error/let-nom> [payload (avro/serialize schema data)]
                     (command/send dispatcher
                                   (command/req->command-request request
                                                                 command-name
                                                                 payload)))]
        (cond
         (= (error/kind result) :command/timeout)
         {:status 408
          :body (command/req->command-response request
                                               result)}

         (error/anomaly? result)
         {:status 500
          :body (command/req->command-response request
                                               result)}

         (= "REJECTED" (:status result))
         {:status 422 :body result}

         :else
         {:status 200
          :body (decode-payload schemas
                                command-name
                                result)})))))

(defn open-account
  [request]
  (send-command request "open-account" (get-in request [:parameters :body])))

(defn close-account
  [request]
  (let [{:keys [account-id]} (get-in request [:parameters :path])]
    (send-command request "close-account" {:account-id account-id})))
