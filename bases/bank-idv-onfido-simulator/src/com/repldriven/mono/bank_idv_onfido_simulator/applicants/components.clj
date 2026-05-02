(ns com.repldriven.mono.bank-idv-onfido-simulator.applicants.components
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.applicants.examples
     :as examples]
    [com.repldriven.mono.bank-idv-onfido-simulator.schema :as schema]))

(def CreateApplicantRequest
  [:map
   {:json-schema/example examples/CreateApplicantRequest}
   [:first_name string?]
   [:last_name string?]
   [:dob {:optional true} [:maybe string?]]])

(def Applicant
  [:map
   {:json-schema/example examples/Applicant}
   [:id string?]
   [:created_at string?]
   [:first_name string?]
   [:last_name string?]
   [:dob {:optional true} [:maybe string?]]])

(def registry
  (schema/components-registry [#'CreateApplicantRequest #'Applicant]))
