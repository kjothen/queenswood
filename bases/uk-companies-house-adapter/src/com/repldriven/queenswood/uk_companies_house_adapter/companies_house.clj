(ns com.repldriven.queenswood.uk-companies-house-adapter.companies-house
  "The Companies House REST contract: the outbound call and the
  translation from its snake_case JSON into the company record shape.
  Anomalies stay in the vendor-neutral `:company/*` namespace — they
  surface as the API's RFC 9457 `type`, which must not name a provider."
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.utility.interface :refer [assoc-some]]))

(defn fetch-company
  "GET `{companies-house-url}/company/{company-number}`. Returns the
  parsed JSON body as a Clojure map (snake_case keys, per the
  Companies House contract) or an anomaly. A 404 is a rejection — the
  registry has no such company — not an internal failure."
  [{:keys [companies-house-url]} company-number]
  (let [res (http/request {:method :get
                           :url (str companies-house-url
                                     "/company/"
                                     company-number)
                           :headers {"Accept" "application/json"}})]
    (cond
     (error/anomaly? res)
     res

     (= 404 (:status res))
     (error/reject :company/not-found
                   {:message "Company not found"
                    :company-number company-number})

     (>= (:status res) 400)
     (error/fail :company/http
                 {:message "Companies House API error"
                  :company-number company-number
                  :status (:status res)
                  :body (:body res)})

     :else
     (let [body (http/res->edn res)]
       (if (error/anomaly? body)
         (error/fail :company/parse
                     {:message "Failed to parse Companies House response"
                      :company-number company-number
                      :cause body})
         body)))))

(defn- address->record
  [{:keys [address_line_1 locality postal_code country]}]
  (assoc-some {}
              :address-line-1 address_line_1
              :locality locality
              :postal-code postal_code
              :country country))

(defn body->company
  "Translate a Companies House company profile into the record shape the
  `company` brick persists."
  [{:keys [company_number company_name company_status type jurisdiction
           date_of_creation registered_office_address]}]
  (assoc-some {:company-number company_number}
              :company-name company_name
              :company-status company_status
              :type type
              :jurisdiction jurisdiction
              :date-of-creation date_of_creation
              :registered-office-address
              (when registered_office_address
                (address->record registered_office_address))))
