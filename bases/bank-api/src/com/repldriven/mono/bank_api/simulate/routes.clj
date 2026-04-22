(ns com.repldriven.mono.bank-api.simulate.routes
  (:require
    [com.repldriven.mono.bank-api.simulate.examples :refer
     [BalanceNotFound SettlementAccountNotFound]]
    [com.repldriven.mono.bank-api.simulate.handlers :as handlers]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.components :as shared.components]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def routes
  [["/simulate"
    {:openapi {:tags ["Simulate"] :security [{"adminAuth" []}]}}
    ["/organizations/{org-id}"
     {:parameters {:path {:org-id [:ref "OrganizationId"]}}}
     ["/inbound-transfer"
      {:post {:summary "Simulate an inbound transfer"
              :openapi {:operationId "SimulateInboundTransfer"
                        :requestBody {:required true}}
              :parameters {:header shared.components/IdempotencyKeyHeader
                           :body [:ref "SimulateInboundTransferRequest"]}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref
                                      "SimulateInboundTransferResponse"]}
                          404 (ErrorResponse [#'BalanceNotFound])}
              :handler handlers/inbound-transfer}}]
     ["/accrue"
      {:post {:summary "Accrue daily interest"
              :openapi {:operationId "SimulateAccrue"
                        :requestBody {:required true}}
              :parameters {:header shared.components/IdempotencyKeyHeader
                           :body [:ref "SimulateInterestRequest"]}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref
                                      "SimulateInterestResponse"]}
                          404 (ErrorResponse [#'SettlementAccountNotFound])}
              :handler handlers/accrue}}]
     ["/capitalize"
      {:post {:summary "Capitalize monthly interest"
              :openapi {:operationId "SimulateCapitalize"
                        :requestBody {:required true}}
              :parameters {:header shared.components/IdempotencyKeyHeader
                           :body [:ref "SimulateInterestRequest"]}
              :interceptors [telemetry/require-idempotency-key]
              :responses {200 {:body [:ref
                                      "SimulateInterestResponse"]}
                          404 (ErrorResponse [#'SettlementAccountNotFound])}
              :handler handlers/capitalize}}]]]])
