(ns com.repldriven.mono.symmetric-key-api.handler
  (:require [clojure.edn :as edn]
            [com.repldriven.mono.encryption.interface :as encryption]))

(defn- handle
  ([status body] {:status (or status 404) :body body})
  ([status] (handle status nil)))

(defn options [_] (handle 200))

(defn other [_] (handle 404 {:errors {:other ["Route not found."]}}))

(defn symmetric-keys [_] (handle 200))
