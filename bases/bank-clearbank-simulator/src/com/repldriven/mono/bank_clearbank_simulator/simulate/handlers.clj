(ns com.repldriven.mono.bank-clearbank-simulator.simulate.handlers
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.webhook :as webhook]

    [com.repldriven.mono.utility.interface :refer [uuidv7]]))

(defn inbound-payment
  [config]
  (fn [request]
    (let [body (get-in request [:parameters :body])
          e2e-id (str (uuidv7))]
      (future
       (webhook/fire-transaction-settled
        config
        e2e-id
        :credit
        body))
      {:status 202
       :body {:endToEndIdentification e2e-id}})))
