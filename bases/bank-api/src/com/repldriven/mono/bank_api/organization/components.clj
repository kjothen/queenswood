(ns com.repldriven.mono.bank-api.organization.components
  (:require
    [com.repldriven.mono.bank-api.organization.coercion :as coercion]
    [com.repldriven.mono.bank-api.organization.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def OrganizationId
  (schema/id-schema "OrganizationId" "org" examples/OrganizationId))

(def OrganisationType
  (coercion/organization-type-enum-schema {:json-schema/example "customer"}))

(def OrganizationStatus
  (coercion/organization-status-enum-schema {:json-schema/example "test"}))

(def CreateOrganizationRequest
  [:map {:json-schema/example examples/CreateOrganizationRequest}
   [:name [:ref "Name"]]
   [:status [:ref "OrganizationStatus"]]
   [:tier-id [:ref "TierId"]]
   [:currencies [:vector [:ref "Currency"]]]])

(def Organization
  [:map {:json-schema/example examples/Organization}
   [:organization-id [:ref "OrganizationId"]]
   [:name [:ref "Name"]]
   [:type [:ref "OrganisationType"]]
   [:status [:ref "OrganizationStatus"]]
   [:tier-id [:ref "TierId"]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:api-key [:ref "ApiKey"]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def OrganizationList
  [:map {:json-schema/example examples/OrganizationList}
   [:organizations [:vector [:ref "Organization"]]]])

(def CreateOrganizationResponse
  [:map {:json-schema/example examples/CreateOrganizationResponse}
   [:organization-id [:ref "OrganizationId"]]
   [:name [:ref "Name"]]
   [:type [:ref "OrganisationType"]]
   [:status [:ref "OrganizationStatus"]]
   [:tier-id [:ref "TierId"]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:api-key [:ref "ApiKey"]]
   [:api-key-secret string?]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def registry
  (components-registry [#'OrganizationId #'OrganisationType #'OrganizationStatus
                        #'CreateOrganizationRequest #'Organization
                        #'OrganizationList #'CreateOrganizationResponse]))
