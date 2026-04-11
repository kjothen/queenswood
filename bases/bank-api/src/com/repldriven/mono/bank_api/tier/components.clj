(ns com.repldriven.mono.bank-api.tier.components
  (:require
    [com.repldriven.mono.bank-api.tier.coercion :as coercion]
    [com.repldriven.mono.bank-api.tier.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer
     [components-registry]]))

(def TierType (coercion/tier-type-enum-schema {:json-schema/example "micro"}))

(def PolicyCapability
  (coercion/policy-capability-enum-schema {:json-schema/example
                                           "account-opening"}))

(def PolicyEffect
  (coercion/policy-effect-enum-schema {:json-schema/example "allow"}))

(def Policy
  [:map {:json-schema/example examples/Policy}
   [:capability [:ref "PolicyCapability"]]
   [:effect [:ref "PolicyEffect"]]
   [:reason {:optional true} [:maybe string?]]])

(def Limit
  [:map {:json-schema/example examples/Limit}
   [:type keyword?]
   [:kind {:optional true} [:maybe map?]]
   [:value {:optional true} [:maybe int?]]
   [:reason {:optional true} [:maybe string?]]])

(def Tier
  [:map {:json-schema/example examples/Tier}
   [:tier-type [:ref "TierType"]]
   [:policies [:vector [:ref "Policy"]]]
   [:limits [:vector [:ref "Limit"]]]
   [:created-at {:optional true}
    [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true}
    [:maybe [:ref "Timestamp"]]]])

(def TierList
  [:map {:json-schema/example examples/TierList}
   [:tiers [:vector [:ref "Tier"]]]])

(def ReplaceTierRequest
  [:map {:json-schema/example examples/ReplaceTierRequest}
   [:policies [:vector [:ref "Policy"]]]
   [:limits [:vector [:ref "Limit"]]]])

(def registry
  (components-registry [#'TierType #'PolicyCapability #'PolicyEffect #'Policy
                        #'Limit #'Tier #'TierList #'ReplaceTierRequest]))
