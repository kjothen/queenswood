(ns com.repldriven.mono.bank-uk-companies-house-simulator.companies.components
  (:require
    [com.repldriven.mono.bank-uk-companies-house-simulator.companies.examples
     :as examples]
    [com.repldriven.mono.bank-uk-companies-house-simulator.schema :as schema]))

(def RegisteredOfficeAddress
  [:map
   {:json-schema/example examples/RegisteredOfficeAddress}
   [:address_line_1 {:optional true} string?]
   [:locality {:optional true} string?]
   [:postal_code {:optional true} string?]
   [:country {:optional true} string?]])

(def Company
  [:map
   {:json-schema/example examples/Company}
   [:company_number string?]
   [:company_name string?]
   [:company_status string?]
   [:type string?]
   [:jurisdiction string?]
   [:date_of_creation string?]
   [:registered_office_address [:ref "RegisteredOfficeAddress"]]])

(def registry
  (schema/components-registry [#'RegisteredOfficeAddress #'Company]))
