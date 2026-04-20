(ns com.repldriven.mono.bank-clearbank-adapter.cop.handlers
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]))

(defn- ->clearbank-request
  [clearbank-url request]
  (let [{:keys [creditor-name account account-type]} request
        {:keys [sort-code account-number]} account]
    {:method :post
     :url (str clearbank-url "/v1/confirmation-of-payee/outbound")
     :headers {"Content-Type" "application/json"}
     :body (json/write-str
            {:accountDetails {:sortCode sort-code
                              :accountNumber account-number}
             :accountHolderName creditor-name
             :accountType (case account-type
                            :account-type-personal "Personal"
                            :account-type-business "Business"
                            "Personal")
             :endToEndIdentification "cop-api"})}))

(defn- ->match-result
  [s]
  (case s
    "Match" :match-result-match
    "CloseMatch" :match-result-close-match
    "NoMatch" :match-result-no-match
    "Unavailable" :match-result-unavailable
    :match-result-unavailable))

(defn- ->result
  [response-body]
  (let [{:keys [matchResult actualName reasonCode reason]} response-body]
    {:match-result (->match-result matchResult)
     :actual-name actualName
     :reason-code reasonCode
     :reason reason}))

(def ^:private unavailable-result
  {:match-result :match-result-unavailable
   :reason-code "ACNS"
   :reason "CoP service unavailable"})

(defn outbound-cop
  [_config]
  (fn [request]
    (let [{:keys [clearbank-url parameters]} request
          {:keys [body]} parameters
          {:keys [creditor-name]} body
          _ (log/info "Outbound CoP check" {:creditor-name creditor-name})
          res (error/try-nom
               :clearbank/cop
               "CoP request failed"
               (http/request (->clearbank-request clearbank-url body)))]
      (if (and (map? res) (= 200 (:status res)))
        {:status 200 :body (->result (http/res->edn res))}
        {:status 200 :body unavailable-result}))))
