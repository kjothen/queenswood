(ns com.repldriven.mono.bank-api.organization.components
  (:require
    [com.repldriven.mono.bank-api.organization.coercion :as coercion]
    [com.repldriven.mono.bank-api.organization.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def OrganisationType
  (coercion/organization-type-enum-schema {:json-schema/example "customer"}))

(def CreateOrganizationRequest
  [:map {:json-schema/example examples/CreateOrganizationRequest}
   [:name string?]
   [:currencies [:vector [:ref "Currency"]]]
   [:policies {:optional true}
    [:maybe [:ref "PolicyList"]]]
   [:limits {:optional true}
    [:maybe [:ref "LimitList"]]]])

(def Organization
  [:map {:json-schema/example examples/Organization}
   [:organization-id string?]
   [:name string?]
   [:type [:ref "OrganisationType"]]
   [:status string?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:api-key [:ref "ApiKey"]]
   [:policies {:optional true}
    [:maybe [:ref "PolicyList"]]]
   [:limits {:optional true}
    [:maybe [:ref "LimitList"]]]])

(def OrganizationList
  [:map {:json-schema/example examples/OrganizationList}
   [:organizations [:vector [:ref "Organization"]]]])

(def CreateOrganizationResponse
  [:map {:json-schema/example examples/CreateOrganizationResponse}
   [:organization-id string?]
   [:name string?]
   [:type [:ref "OrganisationType"]]
   [:status string?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:api-key [:ref "ApiKey"]]
   [:api-key-secret string?]
   [:policies {:optional true}
    [:maybe [:ref "PolicyList"]]]
   [:limits {:optional true}
    [:maybe [:ref "LimitList"]]]])

(def registry
  (components-registry [#'OrganisationType #'CreateOrganizationRequest
                        #'Organization #'OrganizationList
                        #'CreateOrganizationResponse]))
