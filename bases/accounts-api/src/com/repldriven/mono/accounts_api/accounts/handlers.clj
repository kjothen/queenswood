(ns com.repldriven.mono.accounts-api.accounts.handlers
  (:require
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.message-bus.interface :as message-bus]))

(defn- send-command
  [request command-name data]
  (let [bus (:message-bus request)
        schemas (:avro request)
        schema (get schemas command-name)]
    (if-not schema
      {:status 400
       :body (command/req->command-response
              request
              (error/fail :accounts-api/unknown-command
                          {:message "No Avro schema for command"
                           :command command-name}))}
      (let [p (promise)
            result
            (error/let-nom> [payload (avro/serialize schema data)
                             _ (message-bus/subscribe bus
                                                      :command-response
                                                      (fn [d] (deliver p d)))
                             _ (message-bus/send bus
                                                 :command
                                                 (command/req->command-request
                                                  request
                                                  command-name
                                                  payload))]
              (deref p 5000 ::timeout))]
        (cond
         (error/anomaly? result)
         {:status 500
          :body (command/req->command-response request result)}

         (= result ::timeout)
         {:status 408
          :body (command/req->command-response
                 request
                 (error/fail :accounts-api/timeout
                             "Command reply timed out"))}

         :else
         {:status 200 :body result})))))

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
