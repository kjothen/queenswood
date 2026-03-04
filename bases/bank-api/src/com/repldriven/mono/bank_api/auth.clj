(ns com.repldriven.mono.bank-api.auth
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.string :as str]))

(defn- extract-bearer
  [request]
  (some-> (get-in request [:headers "authorization"])
          (str/split #" " 2)
          (as-> parts (when (= "Bearer" (first parts)) (second parts)))))

(def authenticate
  {:name ::authenticate
   :enter (fn [ctx]
            (let [request (:request ctx)
                  raw-key (extract-bearer request)
                  admin-api-key (:admin-api-key request)]
              (cond
               (nil? raw-key)
               ctx

               (encryption/bytes-equals? (util/str->bytes raw-key)
                                         (util/str->bytes admin-api-key))
               (assoc-in ctx [:request :auth] {:role :admin})

               :else
               (assoc-in ctx [:request :auth] {:role :org}))))})
