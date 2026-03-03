(ns com.repldriven.mono.bank-api.auth
  (:require
    [buddy.core.bytes :as bytes]

    [clojure.string :as str]))

(defn- extract-bearer
  [request]
  (some-> (get-in request [:headers "authorization"])
          (str/split #" " 2)
          (as-> parts (when (= "Bearer" (first parts)) (second parts)))))

(defn- admin-key?
  [raw-key expected]
  (and (some? expected)
       (bytes/equals? (.getBytes ^String raw-key)
                      (.getBytes ^String expected))))

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  raw-key (extract-bearer request)
                  expected (:admin-api-key request)]
              (cond
               (nil? raw-key)
               ctx

               (admin-key? raw-key expected)
               (assoc-in ctx [:request :auth] {:role :admin})

               :else
               (assoc-in ctx [:request :auth] {:role :org}))))})
