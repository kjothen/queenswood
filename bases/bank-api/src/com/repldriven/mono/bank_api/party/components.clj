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
                        #'Party #'NationalIdentifier #'CreatePartyRequest
                        #'CreatePartyResponse #'PartyList]))
