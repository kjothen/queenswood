(ns com.repldriven.mono.bank-idv-onfido-simulator.checks.components
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.checks.examples
     :as examples]
    [com.repldriven.mono.bank-idv-onfido-simulator.schema :as schema]))

(def CreateCheckRequest
  [:map
   {:json-schema/example examples/CreateCheckRequest}
   [:applicant_id string?]
   [:report_names {:optional true} [:maybe [:vector string?]]]
   [:external_id {:optional true} [:maybe string?]]])

(def Check
  [:map
   {:json-schema/example examples/Check}
   [:id string?]
   [:applicant_id string?]
   [:status [:enum "in_progress" "complete"]]
   [:result {:optional true} [:maybe [:enum "clear" "consider"]]]
   [:created_at string?]
   [:completed_at_iso8601 {:optional true} [:maybe string?]]
   [:external_id {:optional true} [:maybe string?]]])

(def registry (schema/components-registry [#'CreateCheckRequest #'Check]))
