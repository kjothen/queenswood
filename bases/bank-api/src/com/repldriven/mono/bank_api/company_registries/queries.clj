(ns com.repldriven.mono.bank-api.company-registries.queries
  (:require
    [com.repldriven.mono.bank-api.company-registries.lookup :as lookup]
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]))

(defn lookup-company
  [request]
  (let [{:keys [registry-id company-number]} (get-in request
                                                     [:parameters :path])
        result (lookup/find-company request registry-id company-number)]
    (cond
     (not (error/anomaly? result))
     {:status 200 :body result}

     ;; check-company reports a 404 as an error anomaly, not a rejection,
     ;; so map it to a 404 explicitly rather than letting it fall to 500.
     (= :company-check/not-found (error/kind result))
     {:status 404
      :body (errors/error-response 404
                                   "REJECTED"
                                   (str (error/kind result))
                                   "No active company found for that number")}

     :else
     (errors/anomaly->response result))))
