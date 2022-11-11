(ns com.repldriven.mono.http.client
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as client]))

(defn request [opts] @(client/request opts))

(defn request-async [opts] (client/request opts))

(defn res->json
  [res]
  (when-let [{:keys [body headers]} res]
    (when (= "application/json" (:content-type headers)) (json/read-str body))))

(comment
  (res->json {:body "{\"a\":1,\"b\":2}"})
  (res->json {:headers {:content-type "application/json"}
              :body "{\"a\":1,\"b\":2}"}))
