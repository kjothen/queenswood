(ns com.repldriven.mono.bank-clearbank-adapter.clearbank
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]))

(defn submit-payment
  "Submits an outbound payment to ClearBank FPS.

  Posts the payment instruction to /v3/payments/fps and
  returns the response or anomaly."
  [config data]
  (let [{:keys [clearbank-url]} config
        {:keys [payment-id end-to-end-id debtor-bban
                creditor-bban creditor-name amount
                currency reference]}
        data]
    (log/info "Submitting outbound payment to ClearBank"
              {:payment-id payment-id
               :end-to-end-id end-to-end-id})
    (error/try-nom
     :clearbank/submit-payment
     "Failed to submit payment to ClearBank"
     (let [body
           (json/write-str
            {:paymentInstructions
             [{:debtorAccount
               {:identification
                {:other
                 {:identification debtor-bban
                  :schemeName {:code "BBAN"}}}}
               :paymentInstructionIdentification payment-id
               :paymentTypeCode "SIP"
               :creditTransfers
               [{:paymentIdentification
                 {:instructionIdentification payment-id
                  :endToEndIdentification end-to-end-id}
                 :amount
                 {:currency currency
                  :instructedAmount (/ amount 100.0)}
                 :creditor
                 {:name creditor-name}
                 :creditorAccount
                 {:identification
                  {:other
                   {:identification creditor-bban
                    :schemeName {:code "BBAN"}}}}
                 :remittanceInformation
                 {:unstructured
                  {:additionalReferenceInformation
                   {:reference (or reference "")}}}}]}]})
           res (http/request
                {:method :post
                 :url (str clearbank-url "/v3/payments/fps")
                 :headers {"Content-Type" "application/json"}
                 :body body})]
       (if (and (:status res) (>= (:status res) 400))
         (error/fail :clearbank/submit-payment
                     {:message "ClearBank rejected payment"
                      :status (:status res)
                      :body (:body res)})
         res)))))
