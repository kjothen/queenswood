(ns com.repldriven.mono.bank-api.tier.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.bank-tier.interface :as tiers]))

(defn replace-tier
  [request]
  (cond
   (nil? (:auth request))
   {:status 401
    :body (error-response
           401
           (error/unauthorized :auth/unauthenticated
                               "Missing or invalid API key"))}
   (not= :admin (get-in request [:auth :role]))
   {:status 403
    :body (error-response
           403
           (error/unauthorized
            :auth/forbidden
            "Insufficient privileges"))}
   :else
   (let [config {:record-db (:record-db request)
                 :record-store (:record-store request)}
         {:keys [tier-type]} (get-in request
                                     [:parameters :path])
         {:keys [policies limits]} (get-in request
                                           [:parameters :body])
         result (tiers/update-tier config
                                   tier-type
                                   policies
                                   limits)]
     (if (error/anomaly? result)
       {:status 500 :body (error-response 500 result)}
       {:status 200 :body result}))))
