(ns com.repldriven.mono.bank-api.accounts.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands])
  (:import
    (java.time Instant)))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [account]
  (-> account
      (update :created-at-ms millis->iso)
      (update :updated-at-ms millis->iso)))

(defn- format-account-response
  [{:keys [status body] :as response}]
  (if (= 200 status) (assoc response :body (format-timestamps body)) response))

(defn open-account
  [request]
  (format-account-response (commands/send request
                                          "open-account"
                                          "account"
                                          (get-in request
                                                  [:parameters :body]))))

(defn close-account
  [request]
  (let [{:keys [account-id]} (get-in request [:parameters :path])]
    (format-account-response (commands/send request
                                            "close-account"
                                            "account"
                                            {:account-id account-id}))))
