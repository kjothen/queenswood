(ns com.repldriven.mono.bank-api.payee-check.routes
  (:require
    [com.repldriven.mono.bank-api.payee-check.examples :refer
     [PayeeCheckNotFound]]
    [com.repldriven.mono.bank-api.payee-check.handlers :as handlers]
    [com.repldriven.mono.bank-api.payee-check.queries :as queries]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]))

(def ^:private list-query-schema
  [:map {:closed true} [:page {:optional true} [:ref "PageQuery"]]])

(def routes
  [["/payee-checks"
    {:openapi {:tags ["CoP"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "List payee checks"
            :openapi {:operationId "ListPayeeChecks"
                      :parameters shared.parameters/ref-page}
            :parameters {:query list-query-schema}
            :responses {200 {:body [:ref "PayeeCheckList"]}}
            :handler queries/list-checks}
      :post {:summary "Create a payee check"
             :openapi {:operationId "CreatePayeeCheck"
                       :requestBody {:required true}}
             :parameters {:body [:ref "PayeeCheckRequest"]}
             :responses {201 {:body [:ref "PayeeCheck"]}}
             :handler handlers/create-check}}]
    ["/{check-id}"
     {:parameters {:path {:check-id [:ref "CheckId"]}}}
     [""
      {:get {:summary "Retrieve a payee check"
             :openapi {:operationId "GetPayeeCheck"}
             :responses {200 {:body [:ref "PayeeCheck"]}
                         404 (ErrorResponse [#'PayeeCheckNotFound])}
             :handler queries/get-check}}]]]])
