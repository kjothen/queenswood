(ns com.repldriven.mono.bank-clearbank-simulator.simulate.handlers
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.webhook
     :as webhook]

    [com.repldriven.mono.utility.interface :refer [uuidv7]]))

(defn inbound-payment
  [_config]
  (fn [request]
    (let [{:keys [webhooks sort-code parameters]} request
          {:keys [body]} parameters
          e2e-id (str (uuidv7))
          config {:webhooks webhooks}]
      (future
       (webhook/fire-transaction-settled
        config
        sort-code
        e2e-id
        :credit
        body))
      {:status 202
       :body {:endToEndIdentification e2e-id}})))

(defn inbound-cop-request
  [_config]
  (fn [request]
    (let [{:keys [webhooks parameters]} request
          {:keys [body]} parameters
          {:keys [accountDetails]} body
          {:keys [sortCode]} accountDetails
          request-id (str "cop-" (uuidv7))
          config {:webhooks webhooks}]
      (future
       (webhook/fire-inbound-cop-request config
                                         sortCode
                                         request-id
                                         body))
      {:status 202
       :body {:requestId request-id}})))
