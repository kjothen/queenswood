(ns com.repldriven.queenswood.api.party.links
  "OpenAPI 3 `links` objects for party responses.")

(def from-party
  "Links available on a `CreatePartyResponse`."
  {"GetParty" {:operationId "RetrieveParty"
               :parameters {"party-id" "$response.body#/party-id"}}})

(def from-merged-party
  "Links available on a `MergePartyResponse` — `GetParty` for the
  merged-away record plus `GetMergedIntoParty`, following the
  `merged-into-party-id` pointer to the survivor."
  (assoc from-party
         "GetMergedIntoParty"
         {:operationId "RetrieveParty"
          :parameters {"party-id" "$response.body#/merged-into-party-id"}}))
