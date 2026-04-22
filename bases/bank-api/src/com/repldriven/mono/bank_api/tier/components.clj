(ns com.repldriven.mono.bank-api.tier.components
  (:require
    [com.repldriven.mono.bank-api.tier.coercion :as coercion]
    [com.repldriven.mono.bank-api.tier.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def TierId (schema/id-schema "TierId" "tie" examples/TierId))

(def PolicyCapability
  (coercion/policy-capability-enum-schema {:json-schema/example
                                           "account-opening"}))

(def PolicyEffect
  (coercion/policy-effect-enum-schema {:json-schema/example "allow"}))

(def LimitType
  (coercion/limit-type-enum-schema {:json-schema/example "max-accounts"}))

(def Policy
  [:map {:json-schema/example examples/Policy}
   [:capability [:ref "PolicyCapability"]]
   [:effect [:ref "PolicyEffect"]]
   [:reason {:optional true} [:maybe string?]]])

(def Limit
  [:map {:json-schema/example examples/Limit}
   [:type [:ref "LimitType"]]
   [:kind
    {:optional true
     :decode/api coercion/decode-limit-kind
     :encode/api coercion/encode-limit-kind}
    [:maybe map?]]
   [:value {:optional true} [:maybe int?]]
   [:reason {:optional true} [:maybe string?]]])

(def Tier
  [:map {:json-schema/example examples/Tier}
   [:tier-id [:ref "TierId"]]
   [:name [:ref "Name"]]
   [:policies [:vector [:ref "Policy"]]]
   [:limits [:vector [:ref "Limit"]]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def TierList
  [:map {:json-schema/example examples/TierList}
   [:tiers [:vector [:ref "Tier"]]]])

(def CreateTierRequest
  [:map {:json-schema/example examples/CreateTierRequest}
   [:name [:ref "Name"]]
   [:policies [:vector [:ref "Policy"]]]
   [:limits [:vector [:ref "Limit"]]]])

(def ReplaceTierRequest
  [:map {:json-schema/example examples/ReplaceTierRequest}
   [:policies [:vector [:ref "Policy"]]]
   [:limits [:vector [:ref "Limit"]]]])

(def registry
  (components-registry [#'TierId #'PolicyCapability #'PolicyEffect #'LimitType
                        #'Policy #'Limit #'Tier #'TierList #'CreateTierRequest
                        #'ReplaceTierRequest]))
