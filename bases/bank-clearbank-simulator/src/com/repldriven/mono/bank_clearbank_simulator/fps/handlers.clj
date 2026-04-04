(ns com.repldriven.mono.bank-clearbank-simulator.fps.handlers
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.webhook :as webhook]))

(defn payment
  [config]
  (fn [request]
    (let [{:keys [parameters]} request
          {:keys [body]} parameters
          {:keys [paymentInstructions]} body
          {:keys [creditTransfers]} (first paymentInstructions)
          {:keys [paymentIdentification creditor amount]} (first
                                                           creditTransfers)
          {:keys [endToEndIdentification]} paymentIdentification
          {:keys [name]} creditor
          {:keys [instructedAmount currency]} amount
          {:keys [webhook-delay-ms]} config]
      (future
       (Thread/sleep (or webhook-delay-ms 2000))
       (if (= "REJECT" name)
         (webhook/fire-transaction-rejected config endToEndIdentification)
         (webhook/fire-transaction-settled config
                                           endToEndIdentification
                                           :debit
                                           {:amount instructedAmount
                                            :currency currency})))
      {:status 202
       :body
       {:transactions
        [{:endToEndIdentification endToEndIdentification
          :response "Accepted"}]
        :halLinks []}})))
