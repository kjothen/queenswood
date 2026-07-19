(ns com.repldriven.mono.bank-api.party.routes
  (:require
    [com.repldriven.mono.bank-api.party.commands :as commands]
    [com.repldriven.mono.bank-api.party.queries :as queries]
    [com.repldriven.mono.bank-api.party.examples :refer
     [IdentificationRejected PartyNotFound PartyInvalidStatus
      PartyOpenAccounts PartyMergeIntoSelf]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.party.links :as links]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]

    [com.repldriven.mono.bank-idempotency.interface :as bank-idempotency]
    [com.repldriven.mono.server.interface :as server]))

(def ^:private list-parties-query-schema
  [:map {:closed true} [:page {:optional true} [:ref "PageQuery"]]])

(def ^:private get-party-query-schema
  [:map {:closed true} [:embed {:optional true} [:ref "PartyEmbedQuery"]]])

(def routes
  [["/parties" {:openapi {:tags ["Parties"] :security [{"bearerAuth" ["org"]}]}}
    [""
     {:get {:summary "Retrieve parties"
            :openapi {:operationId "RetrieveParties"
                      :parameters ^:replace [shared.parameters/ref-page]}
            :parameters {:query list-parties-query-schema}
            :responses {200 {:body [:ref "PartyList"]}}
            :handler queries/list-parties}
      :post {:summary "Create a new party"
             :openapi {:operationId "CreateParty"
                       :requestBody {:required true}
                       :parameters ^:replace
                                   [shared.parameters/ref-idempotency-key]}
             :interceptors [server/require-idempotency-key
                            bank-idempotency/cache-response]
             :parameters {:body [:ref "CreatePartyRequest"]}
             :responses {200 {:body [:ref "CreatePartyResponse"]
                              :openapi {:links links/from-party}}
                         422 (ErrorResponse [#'IdentificationRejected])}
             :handler commands/create-party}}]
    ["/{party-id}" {:parameters {:path {:party-id [:ref "PartyId"]}}}
     [""
      {:get {:summary "Retrieve a party"
             :openapi {:operationId "RetrieveParty"
                       :parameters ^:replace
                                   [shared.parameters/ref-party-id
                                    shared.parameters/ref-party-embed]}
             :parameters {:query get-party-query-schema}
             :responses {200 {:body [:ref "PartyDetail"]}
                         404 (ErrorResponse [#'PartyNotFound])}
             :handler queries/get-party}}]
     ["/merge"
      {:post {:summary "Merge a party into another"
              :openapi {:operationId "MergeParty"
                        :requestBody {:required true}
                        :parameters ^:replace
                                    [shared.parameters/ref-party-id
                                     shared.parameters/ref-idempotency-key]}
              :interceptors [server/require-idempotency-key
                             bank-idempotency/cache-response]
              :parameters {:body [:ref "MergePartyRequest"]}
              :responses {200 {:body [:ref "MergePartyResponse"]
                               :openapi {:links links/from-merged-party}}
                          404 (ErrorResponse [#'PartyNotFound])
                          409 (ErrorResponse [#'PartyInvalidStatus
                                              #'PartyOpenAccounts])
                          422 (ErrorResponse [#'PartyMergeIntoSelf])}
              :handler commands/merge-party}}]]]])
