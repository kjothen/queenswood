(ns com.repldriven.mono.bank-api.party.links
  "OpenAPI 3 `links` objects for party responses.")

(def from-party
  "Links available on a `CreatePartyResponse`."
  {"GetParty" {:operationId "RetrieveParty"
               :parameters {"party-id" "$response.body#/party-id"}}})
