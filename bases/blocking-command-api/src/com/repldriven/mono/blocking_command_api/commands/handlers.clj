(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require
    [com.repldriven.mono.log.interface :as log]
    [clojure.pprint :as pp]
    [clojure.string :as str]))

(defn create
  [request]
  (let [{:keys [parameters mqtt-client pulsar-producer]} request
        {:keys [body]} parameters
        {:keys [data]} body
        {:keys [type id]} data]
    (log/info "\n" (with-out-str (pp/pprint request)))
    {:status 200
     :body {:data {:result (str/join "/" [type id])
                   :mqtt-client (some? mqtt-client)
                   :pulsar-producer (some? pulsar-producer)}}}))
