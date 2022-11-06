(ns com.repldriven.mono.http.interface
  (:require [com.repldriven.mono.http.client :as client]))

(defn request
  [opts]
  (client/request opts))

(defn request-async
  [opts]
  (client/request-async opts))

(defn res->json
  [res]
  (client/res->json res))
