(ns com.repldriven.mono.accounts-api.accounts.handlers
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private response-schema
  {"open-account" "account"
   "close-account" "account"
   "reopen-account" "account"
   "suspend-account" "account"
   "unsuspend-account" "account"
   "archive-account" "account"
   "get-account-status" "account-status"})

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
              (error/fail :accounts-api/unknown-command
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
          :body (command/req->command-response request result)}

         (error/anomaly? result)
         {:status 500
          :body (command/req->command-response request result)}

         (= "REJECTED" (:status result))
         {:status 422 :body result}

         :else
         {:status 200
          :body (decode-payload schemas command-name result)})))))

(defn open-account
  [request]
  (send-command request "open-account" (:body-params request)))

(defn close-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "close-account" {:account-id account-id})))

(defn reopen-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "reopen-account" {:account-id account-id})))

(defn suspend-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "suspend-account" {:account-id account-id})))

(defn unsuspend-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "unsuspend-account" {:account-id account-id})))

(defn archive-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "archive-account" {:account-id account-id})))

(defn get-account-status
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "get-account-status" {:account-id account-id})))
