(ns com.repldriven.mono.bank-api.payee-check.routes
  (:require
    [com.repldriven.mono.bank-api.payee-check.handlers :as handlers]
    [com.repldriven.mono.bank-api.payee-check.queries :as queries]))

(def ^:private list-query-schema
  [:map [(keyword "page[after]") {:optional true} string?]
   [(keyword "page[before]") {:optional true} string?]
   [(keyword "page[size]") {:optional true} string?]])

(def routes
  [["/payee-checks"
    {:openapi {:tags ["CoP"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List payee checks"
            :openapi {:operationId "ListPayeeChecks"}
            :parameters {:query list-query-schema}
            :responses {200 {:body [:ref "PayeeCheckList"]}}
            :handler queries/list-checks}
      :post {:summary "Create a payee check"
             :openapi {:operationId "CreatePayeeCheck"}
             :parameters {:body [:ref "PayeeCheckRequest"]}
             :responses {201 {:body [:ref "PayeeCheck"]}}
             :handler handlers/create-check}}]
    ["/{check-id}"
     {:parameters {:path {:check-id string?}}}
     [""
      {:get {:summary "Retrieve a payee check"
             :openapi {:operationId "GetPayeeCheck"}
             :responses {200 {:body [:ref "PayeeCheck"]} 404 {}}
             :handler queries/get-check}}]]]])
