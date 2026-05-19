(ns com.repldriven.mono.bank-api.party.components
  (:require
    [com.repldriven.mono.bank-api.party.coercion :as coercion]
    [com.repldriven.mono.bank-api.party.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def PartyId (schema/id-schema "PartyId" "pty" examples/PartyId))

(def PartyType
  (coercion/party-type-enum-schema {:json-schema/example "person"}))

(def PartyStatus
  (coercion/party-status-enum-schema {:json-schema/example "active"}))

(def IdentifierType
  (coercion/identifier-type-enum-schema {:json-schema/example "passport"}))

(def Party
  [:map {:json-schema/example examples/Party}
   [:organization-id [:ref "OrganizationId"]]
   [:party-id [:ref "PartyId"]]
   [:type [:ref "PartyType"]]
   [:display-name [:ref "Name"]]
   [:status [:ref "PartyStatus"]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def NationalIdentifier
  [:map {:closed true}
   [:type [:ref "IdentifierType"]]
   [:value [:ref "NationalIdentifierValue"]]
   [:issuing-country [:ref "CountryCode"]]])

(def Address
  "Address shape mirrors the Entrust/Onfido applicant address
  object. `country` is ISO 3166-1 alpha-3 to match what the
  applicants API expects; this differs from `nationality` (alpha-2)
  on PersonIdentification — kept separate so the adapter doesn't
  need a code-table conversion at the edge."
  [:map {:closed true :json-schema/example examples/Address}
   [:flat-number {:optional true} [:ref "Name"]]
   [:building-number {:optional true} [:ref "Name"]]
   [:building-name {:optional true} [:ref "Name"]]
   [:street [:ref "Name"]]
   [:sub-street {:optional true} [:ref "Name"]]
   [:town [:ref "Name"]]
   [:state {:optional true} [:ref "Name"]]
   [:postcode [:ref "Name"]]
   [:country [:ref "Country3Code"]]
   [:start-date {:optional true} [:ref "Date"]]])

(def CreatePartyRequest
  [:map {:json-schema/example examples/CreatePartyRequest}
   [:type
    [:enum
     {:json-schema coercion/party-type-json-schema
      :decode/api coercion/decode-party-type}
     :party-type-person]]
   [:display-name [:ref "Name"]]
   [:given-name [:ref "Name"]]
   [:middle-names {:optional true} [:maybe [:ref "Name"]]]
   [:family-name [:ref "Name"]]
   [:date-of-birth [:ref "DateOfBirth"]]
   [:nationality [:ref "CountryCode"]]
   [:address [:ref "Address"]]
   [:national-identifier [:ref "NationalIdentifier"]]])

(def CreatePartyResponse [:ref "Party"])

(def PartyList
  [:map {:json-schema/example examples/PartyList}
   [:parties [:vector [:ref "Party"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def registry
  (components-registry [#'PartyId #'PartyType #'PartyStatus #'IdentifierType
                        #'Party #'NationalIdentifier #'Address
                        #'CreatePartyRequest #'CreatePartyResponse
                        #'PartyList]))
