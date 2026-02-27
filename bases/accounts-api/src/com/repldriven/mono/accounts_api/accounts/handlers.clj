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
      (let [payload (avro/serialize schema data)]
        (if (error/anomaly? payload)
          {:status 500 :body (command/req->command-response request payload)}
          (let [cmd (command/req->command-request request command-name payload)
                p (promise)
                sub (message-bus/subscribe bus
                                           :command-response
                                           (fn [d] (deliver p d)))]
            (if (error/anomaly? sub)
              {:status 500 :body (command/req->command-response request sub)}
              (try (let [pub (message-bus/send bus :command cmd)]
                     (if (error/anomaly? pub)
                       {:status 500
                        :body (command/req->command-response request pub)}
                       (let [result (deref p 5000 ::timeout)]
                         (if (= result ::timeout)
                           {:status 408
                            :body (command/req->command-response
                                   request
                                   (error/fail :accounts-api/timeout
                                               "Command reply timed out"))}
                           {:status 200 :body result}))))
                   (finally (message-bus/unsubscribe bus
                                                     :command-response))))))))))

(defn open-account
  [request]
  (let [body (:body-params request)]
    (send-command request "open-account" body)))

(defn close-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "close-account" {"account_id" account-id})))

(defn reopen-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "reopen-account" {"account_id" account-id})))

(defn suspend-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "suspend-account" {"account_id" account-id})))

(defn unsuspend-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "unsuspend-account" {"account_id" account-id})))

(defn archive-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "archive-account" {"account_id" account-id})))

(defn get-account-status
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "get-account-status" {"account_id" account-id})))
