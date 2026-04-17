(ns com.repldriven.mono.bank-api.payment.queries
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-payment.interface :as payments]
    [com.repldriven.mono.error.interface :as error]))

(defn get-internal-payment
  [request]
  (let [{:keys [payment-id]} (get-in request [:parameters :path])
        result (payments/get-internal-payment request payment-id)]
    (cond (error/anomaly? result)
          {:status 500 :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "payment/not-found"
                  "Payment not found")}
          :else
          {:status 200 :body result})))

(defn get-outbound-payment
  [request]
  (let [{:keys [payment-id]} (get-in request [:parameters :path])
        result (payments/get-outbound-payment request payment-id)]
    (cond (error/anomaly? result)
          {:status 500 :body (error-response 500 result)}
          (nil? result)
          {:status 404
           :body (error-response
                  404 "REJECTED"
                  "payment/not-found"
                  "Payment not found")}
          :else
          {:status 200 :body result})))
