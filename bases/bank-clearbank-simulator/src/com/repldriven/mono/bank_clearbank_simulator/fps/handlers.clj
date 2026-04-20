(ns com.repldriven.mono.bank-clearbank-simulator.fps.handlers
  (:require
    [com.repldriven.mono.bank-clearbank-simulator.webhook
     :as webhook]

    [com.repldriven.mono.utility.interface :refer [uuidv7]]))

(defn payment
  [_config]
  (fn [request]
    (let [{:keys [webhooks sort-code webhook-delay-ms parameters]}
          request
          {:keys [body]} parameters
          {:keys [paymentInstructions]} body
          instruction (first paymentInstructions)
          {:keys [creditTransfers]} instruction
          transfer (first creditTransfers)
          {:keys [paymentIdentification creditor
                  creditorAccount amount
                  remittanceInformation]}
          transfer
          {:keys [endToEndIdentification]} paymentIdentification
          {:keys [name]} creditor
          creditor-bban (get-in creditorAccount
                                [:identification :other
                                 :identification])
          reference (get-in remittanceInformation
                            [:unstructured
                             :additionalReferenceInformation
                             :reference])
          {:keys [instructedAmount currency]} amount
          config {:webhooks webhooks}]
      (future
       (Thread/sleep (or webhook-delay-ms 2000))
       (if (= "REJECT" name)
         (webhook/fire-transaction-rejected
          config
          sort-code
          endToEndIdentification)
         (do
           (webhook/fire-transaction-settled
            config
            sort-code
            endToEndIdentification
            :debit
            {:amount instructedAmount
             :currency currency
             :reference reference})
           (Thread/sleep 500)
           (webhook/fire-transaction-settled
            config
            sort-code
            (str (uuidv7))
            :credit
            {:amount instructedAmount
             :currency currency
             :creditor-bban creditor-bban
             :debtor-name name
             :reference reference}))))
      {:status 202
       :body
       {:transactions
        [{:endToEndIdentification endToEndIdentification
          :response "Accepted"}]
        :halLinks []}})))
