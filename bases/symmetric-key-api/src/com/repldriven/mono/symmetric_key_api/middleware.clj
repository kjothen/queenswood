(ns com.repldriven.mono.symmetric-key-api.middleware
  (:require [clojure.string :as str]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.env.interface :as env]))

(defn wrap-exceptions
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (let [message (str "An unknown exception occurred.")]
          (log/error e message)
          {:status 500
           :body   {:errors {:other [message]}}})))))

(defn create-access-control-header
  [origin]
  (let [allowed-origins (or (@env/env :allowed-origins) "")
        origins (str/split allowed-origins #",")
        allowed-origin (some #{origin} origins)]
    {"Access-Control-Allow-Origin"  allowed-origin
     "Access-Control-Allow-Methods" "POST, GET, PUT, OPTIONS, DELETE"
     "Access-Control-Max-Age"       "3600"
     "Access-Control-Allow-Headers" "Authorization, Content-Type, x-requested-with"}))

(defn wrap-cors
  [handler]
  (fn [req]
    (let [origin (get (:headers req) "origin")
          response (handler req)]
      (update-in response [:headers] merge (create-access-control-header origin)))))
