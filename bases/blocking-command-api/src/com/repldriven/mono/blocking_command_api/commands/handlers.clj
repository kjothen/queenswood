(ns com.repldriven.mono.blocking-command-api.commands.handlers
  (:require [clojure.string :as str]))

(defn create
  [{{{{:keys [type id]} :data} :body} :parameters}]
  {:status 200
   :body {:data {:result (str/join "/" [type id])}}})
