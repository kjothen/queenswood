(ns com.repldriven.mono.bank-api.organizations.components
  (:require
    [com.repldriven.mono.bank-api.organizations.examples :as examples]

    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def CreateOrganizationRequest
  [:map
   {:json-schema/example examples/CreateOrganizationRequest}
   [:name string?]])

(def Organization
  [:map
   {:json-schema/example examples/Organization}
   [:organization-id string?]
   [:name string?]
   [:status string?]])

(def CreateOrganizationResponse
  [:map
   {:json-schema/example examples/CreateOrganizationResponse}
   [:organization [:ref "Organization"]]
   [:api-key [:ref "ApiKeyResponse"]]])

(def registry
  (components-registry [#'CreateOrganizationRequest #'Organization
                        #'CreateOrganizationResponse]))
