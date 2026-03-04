(ns com.repldriven.mono.bank-api.organizations.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.organizations.interface :as organizations]))

(defn create-organization
  [request]
  (cond
   (nil? (:auth request))
   {:status 401
    :body (errors/error-response 401 "UNAUTHORIZED"
                                 "unauthorized"
                                 "Unauthorized")}

   (not= :admin (get-in request [:auth :role]))
   {:status 403
    :body (errors/error-response 403 "UNAUTHORIZED"
                                 "forbidden" "Forbidden")}

   :else
   (let [{:keys [record-db record-store]} request
         {:keys [name]} (get-in request [:parameters :body])
         result (organizations/create-organization
                 {:record-db record-db :record-store record-store}
                 name)]
     (if (error/anomaly? result)
       {:status 500
        :body (errors/error-response 500 result)}
       {:status 201
        :body {:organization (:organization result)
               :api-key {:id (get-in result [:api-key :id])
                         :key-prefix (get-in result [:api-key :key-prefix])
                         :raw-key (:raw-key result)}}}))))
