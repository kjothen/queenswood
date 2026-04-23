(ns com.repldriven.mono.bank-api.party.routes
  (:require
    [com.repldriven.mono.bank-api.party.commands :as commands]
    [com.repldriven.mono.bank-api.party.queries :as queries]
    [com.repldriven.mono.bank-api.party.examples :refer
     [DuplicateNationalIdentifier PartyNotFound]]
    [com.repldriven.mono.bank-api.schema :refer [ErrorResponse]]
    [com.repldriven.mono.bank-api.shared.parameters :as shared.parameters]
    [com.repldriven.mono.telemetry.interface :as telemetry]))

(def ^:private list-parties-query-schema
  [:map {:closed true} [:page {:optional true} [:ref "PageQuery"]]])

(def routes
  [["/parties" {:openapi {:tags ["Parties"] :security [{"orgAuth" []}]}}
    [""
     {:get {:summary "Retrieve parties"
            :openapi {:operationId "RetrieveParties"
                      :parameters shared.parameters/ref-page}
            :parameters {:query list-parties-query-schema}
            :responses {200 {:body [:ref "PartyList"]}}
            :handler queries/list-parties}
      :post {:summary "Create a new party"
             :openapi {:operationId "CreateParty"
                       :requestBody {:required true}
                       :parameters shared.parameters/ref-idempotency-key}
             :interceptors [telemetry/require-idempotency-key]
             :parameters {:body [:ref "CreatePartyRequest"]}
             :responses {200 {:body [:ref "CreatePartyResponse"]}
                         409 (ErrorResponse [#'DuplicateNationalIdentifier])}
             :handler commands/create-party}}]
    ["/{party-id}" {:parameters {:path {:party-id [:ref "PartyId"]}}}
     [""
      {:get {:summary "Retrieve a party"
             :openapi {:operationId "RetrieveParty"}
             :responses {200 {:body [:ref "Party"]}
                         404 (ErrorResponse [#'PartyNotFound])}
             :handler queries/get-party}}]]]])
