(ns com.repldriven.mono.bank-clearbank-simulator.webhook
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :refer [uuidv7]]))

(defn- now
  []
  (str (java.time.Instant/now)))

(defn- nonce
  []
  (rand-int Integer/MAX_VALUE))

(defn fire
  [config sort-code type payload]
  (let [url (get-in @(:webhooks config) [sort-code type])]
    (if-not url
      (do (log/warn "No webhook registered for"
                    sort-code
                    type)
          nil)
      (let [body (json/write-str
                  {:Type type
                   :Version (case type
                              "TransactionSettled" 6
                              "TransactionRejected" 2
                              1)
                   :Payload payload
                   :Nonce (nonce)})
            res (http/request {:method :post
                               :url url
                               :headers {"Content-Type"
                                         "application/json"}
                               :body body})]
        (when (or (error/anomaly? res)
                  (and (:status res) (>= (:status res) 400)))
          (log/error "Webhook delivery failed for" type
                     "to" url
                     ":" res))
        res))))

(defn fire-transaction-settled
  [config sort-code e2e-id debit-credit-code body]
  (let [{:keys [bban amount currency reference
                creditor-bban debtor-name]}
        body]
    (fire config
          sort-code
          "TransactionSettled"
          {:TransactionId (str (uuidv7))
           :Status "Settled"
           :Scheme "FasterPayments"
           :EndToEndTransactionId e2e-id
           :Amount amount
           :CurrencyCode (or currency "GBP")
           :DebitCreditCode (if (= :credit debit-credit-code)
                              "Credit"
                              "Debit")
           :TimestampSettled (now)
           :TimestampCreated (now)
           :Reference (or reference "")
           :IsReturn false
           :Account {:BBAN (or creditor-bban bban)}
           :CounterpartAccount
           {:OwnerName (or debtor-name "Simulated Debtor")}})))

(defn fire-transaction-rejected
  [config sort-code e2e-id]
  (fire config
        sort-code
        "TransactionRejected"
        {:TransactionId (str (uuidv7))
         :Status "Rejected"
         :Scheme "FasterPayments"
         :EndToEndTransactionId e2e-id
         :CancellationCode "AM09"
         :CancellationReason "Insufficient funds"
         :DebitCreditCode "Debit"
         :IsReturn false
         :Account {}
         :CounterpartAccount {}}))

(defn fire-inbound-cop-request
  [config sort-code request-id body]
  (let [{:keys [accountDetails accountHolderName
                accountType requestingInstitution]}
        body
        {:keys [sortCode accountNumber]} accountDetails]
    (fire config
          sort-code
          "InboundCopRequestReceived"
          {:RequestId request-id
           :RequestingInstitution (or requestingInstitution "")
           :AccountHolderName accountHolderName
           :ProductType accountType
           :AccountDetails {:SortCode sortCode
                            :AccountNumber accountNumber}
           :TimestampCreated (now)})))
