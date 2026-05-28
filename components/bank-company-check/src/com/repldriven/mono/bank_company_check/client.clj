(ns com.repldriven.mono.bank-company-check.client
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]))

(defn fetch-company
  "GET `{api-url}/company/{company-number}`. Returns the parsed
  JSON body as a Clojure map (snake_case keys, per the
  Companies House contract) or an anomaly."
  [{:keys [api-url]} company-number]
  (let [res (http/request {:method :get
                           :url (str api-url "/company/" company-number)
                           :headers {"Accept" "application/json"}})]
    (cond
     (error/anomaly? res)
     res

     (= 404 (:status res))
     (error/fail :company-check/not-found
                 {:message "Company not found"
                  :company-number company-number})

     (>= (:status res) 400)
     (error/fail :company-check/http
                 {:message "Companies House API error"
                  :company-number company-number
                  :status (:status res)
                  :body (:body res)})

     :else
     (let [body (http/res->edn res)]
       (if (error/anomaly? body)
         (error/fail :company-check/parse
                     {:message "Failed to parse Companies House response"
                      :company-number company-number
                      :cause body})
         body)))))
