(ns com.repldriven.mono.http.client
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as client]))

(defn request
  [opts]
  @(client/request opts))

(defn request-async
  [opts]
  (client/request opts))

(defn res->json
  [res]
  (some-> res :body json/read-str))

(comment
  (res->json {:body "{\"a\":1,\"b\":2}"})
  )
