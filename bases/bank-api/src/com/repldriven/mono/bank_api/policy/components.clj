(ns com.repldriven.mono.bank-api.policy.components
  (:require
    [com.repldriven.mono.bank-api.policy.coercion :as coercion]
    [com.repldriven.mono.bank-api.policy.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def PolicyId (schema/id-schema "PolicyId" "pol" examples/PolicyId))

(def PolicyCategory
  (coercion/policy-category-enum-schema {:json-schema/example "restricted"}))

;; Capability and Limit bodies are polymorphic oneofs over kind
;; (`:balance`, `:cash-account`, …). Rather than mirror the proto's
;; nested structure in malli — which balloons fast and breaks every
;; time the proto evolves — we expose them as open maps. The
;; frontend reads field-by-field defensively.
(def Capability [:map {:json-schema/example examples/Capability}])

(def Limit [:map {:json-schema/example examples/Limit}])

(def Policy
  [:map {:json-schema/example examples/Policy}
   [:policy-id [:ref "PolicyId"]]
   [:name {:optional true} [:maybe [:ref "Name"]]]
   [:description {:optional true} [:maybe string?]]
   [:enabled boolean?]
   [:category [:ref "PolicyCategory"]]
   [:capabilities [:vector [:ref "Capability"]]]
   [:limits [:vector [:ref "Limit"]]]
   [:labels [:map-of string? string?]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def PolicyList
  [:map {:json-schema/example examples/PolicyList}
   [:policies [:vector [:ref "Policy"]]]])

(def registry
  (components-registry [#'PolicyId #'PolicyCategory #'Capability #'Limit
                        #'Policy #'PolicyList]))
