(ns com.repldriven.mono.bank-api.payee-check.handlers
  (:require
    [com.repldriven.mono.bank-api.errors :refer [error-response]]

    [com.repldriven.mono.bank-payee-check.interface :as payee-checks]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]))

(defn- perform-cop-check
  [adapter-url body]
  (let [{:keys [creditor-name account account-type]} body
        {:keys [sort-code account-number]} account
        res (error/try-nom
             :payee-check/cop
             "CoP request to adapter failed"
             (http/request
              {:method :post
               :url (str adapter-url "/cop/outbound")
               :headers {"Content-Type" "application/json"}
               :body (json/write-str
                      {:creditor-name creditor-name
                       :account {:sort-code sort-code
                                 :account-number account-number}
                       :account-type account-type})}))]
    (if (error/anomaly? res)
      {:match-result :match-result-unavailable
       :reason-code "ACNS"
       :reason "CoP service unavailable"}
      (if (= 200 (:status res))
        (let [{:keys [match-result actual-name reason-code reason]}
              (http/res->edn res)]
          {:match-result (keyword match-result)
           :actual-name actual-name
           :reason-code reason-code
           :reason reason})
        {:match-result :match-result-unavailable
         :reason-code "ACNS"
         :reason "CoP service unavailable"}))))

(defn create-check
  [request]
  (let [{:keys [record-db record-store clearbank-adapter-url
                auth parameters]}
        request
        {:keys [body]} parameters
        {:keys [organization-id]} auth
        config {:record-db record-db :record-store record-store}
        cop-result (perform-cop-check clearbank-adapter-url body)
        result (payee-checks/check-payee config
                                         organization-id
                                         body
                                         cop-result)]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      {:status 201 :body result})))
