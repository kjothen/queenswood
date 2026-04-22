(ns com.repldriven.mono.bank-api.payment.queries
  (:require
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-payment.interface :as payments]
    [com.repldriven.mono.error.interface :as error]))

(defn get-internal-payment
  [request]
  (let [{:keys [payment-id]} (get-in request [:parameters :path])
        result (payments/get-internal-payment request payment-id)]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          (nil? result)
          {:status 404
           :body (errors/error-response
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
          (errors/anomaly->response result)
          (nil? result)
          {:status 404
           :body (errors/error-response
                  404 "REJECTED"
                  "payment/not-found"
                  "Payment not found")}
          :else
          {:status 200 :body result})))
