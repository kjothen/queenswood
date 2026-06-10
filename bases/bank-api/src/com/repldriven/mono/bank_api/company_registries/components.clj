(ns com.repldriven.mono.bank-api.company-registries.components
  (:require
    [com.repldriven.mono.bank-api.company-registries.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def RegisteredOfficeAddress
  [:map
   [:address-line-1 {:optional true} string?]
   [:locality {:optional true} string?]
   [:postal-code {:optional true} string?]
   [:country {:optional true} string?]])

(def Company
  "A company profile in the Companies House shape, as resolved from a
  registry lookup."
  [:map {:json-schema/example examples/Company}
   [:company-number string?]
   [:company-name {:optional true} string?]
   [:company-status {:optional true} string?]
   [:type {:optional true} string?]
   [:jurisdiction {:optional true} string?]
   [:date-of-creation {:optional true} string?]
   [:registered-office-address {:optional true}
    [:ref "RegisteredOfficeAddress"]]])

(def registry (components-registry [#'RegisteredOfficeAddress #'Company]))
