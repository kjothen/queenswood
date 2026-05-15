(ns com.repldriven.mono.bank-api.simulate.routes
  (:require
    [com.repldriven.mono.bank-api.organization.examples :refer
     [OrganizationNotFound]]
    [com.repldriven.mono.bank-api.simulate.examples :refer
     [BalanceNotFound InvalidAmount SettlementAccountNotFound
      TransactionAlreadyRecorded]]
    [com.repldriven.mono.bank-api.simulate.handlers :as handlers]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]

    [com.repldriven.mono.bank-idempotency.interface :as bank-idempotency]
    [com.repldriven.mono.server.interface :as server]))

(def routes
  [["/simulate"
    {:openapi {:tags ["Simulate"] :security [{"bearerAuth" ["admin"]}]}}
    ["/organizations/{org-id}"
     {:parameters {:path {:org-id [:ref "OrganizationId"]}}}
     ["/inbound-transfer"
      {:post {:summary "Simulate an inbound transfer"
              :openapi {:operationId "SimulateInboundTransfer"
                        :requestBody {:required true}
                        :parameters ^:replace
                                    [shared.parameters/ref-org-id
                                     shared.parameters/ref-idempotency-key]}
              :parameters {:body [:ref "SimulateInboundTransferRequest"]}
              :interceptors [server/require-idempotency-key
                             bank-idempotency/cache-response]
              :responses {200 {:body [:ref
                                      "SimulateInboundTransferResponse"]}
                          404 (ErrorResponse [#'OrganizationNotFound
                                              #'BalanceNotFound])
                          422 (ErrorResponse [#'TransactionAlreadyRecorded
                                              #'InvalidAmount])}
              :handler handlers/inbound-transfer}}]
     ["/accrue"
      {:post {:summary "Accrue interest"
              :openapi {:operationId "SimulateAccrue"
                        :requestBody {:required true}
                        :parameters ^:replace
                                    [shared.parameters/ref-org-id
                                     shared.parameters/ref-idempotency-key]}
              :parameters {:body [:ref "SimulateInterestRequest"]}
              :interceptors [server/require-idempotency-key
                             bank-idempotency/cache-response]
              :responses {200 {:body [:ref
                                      "SimulateInterestResponse"]}
                          404 (ErrorResponse [#'OrganizationNotFound
                                              #'SettlementAccountNotFound])
                          422 (ErrorResponse [#'TransactionAlreadyRecorded])}
              :handler handlers/accrue}}]
     ["/capitalize"
      {:post {:summary "Capitalize interest"
              :openapi {:operationId "SimulateCapitalize"
                        :requestBody {:required true}
                        :parameters ^:replace
                                    [shared.parameters/ref-org-id
                                     shared.parameters/ref-idempotency-key]}
              :parameters {:body [:ref "SimulateInterestRequest"]}
              :interceptors [server/require-idempotency-key
                             bank-idempotency/cache-response]
              :responses {200 {:body [:ref
                                      "SimulateInterestResponse"]}
                          404 (ErrorResponse [#'OrganizationNotFound
                                              #'SettlementAccountNotFound])
                          422 (ErrorResponse [#'TransactionAlreadyRecorded])}
              :handler handlers/capitalize}}]]]])
