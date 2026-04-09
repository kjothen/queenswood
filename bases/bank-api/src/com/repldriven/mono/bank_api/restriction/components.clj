(ns com.repldriven.mono.bank-api.restriction.components
  (:require
    [com.repldriven.mono.bank-api.restriction.coercion
     :as coercion]
    [com.repldriven.mono.bank-api.schema
     :refer [components-registry]]))

(def PolicyCapability
  (coercion/policy-capability-enum-schema {:json-schema/example
                                           "outbound-payments"}))

(def PolicyEffect
  (coercion/policy-effect-enum-schema {:json-schema/example "allow"}))

(def LimitType
  (coercion/limit-type-enum-schema {:json-schema/example "max-accounts"}))

(def LimitKind
  [:map
   [:currency {:optional true} [:maybe string?]]
   [:account-type {:optional true}
    [:maybe [:ref "AccountType"]]]
   [:balance-type {:optional true}
    [:maybe [:ref "BalanceType"]]]
   [:organization-type {:optional true}
    [:maybe [:ref "OrganisationType"]]]])

(def Policy
  [:map
   [:capability [:ref "PolicyCapability"]]
   [:effect [:ref "PolicyEffect"]]
   [:reason {:optional true} [:maybe string?]]])

(def Limit
  [:map
   [:type [:ref "LimitType"]]
   [:kind {:optional true} [:maybe [:ref "LimitKind"]]]
   [:upper {:optional true} [:maybe int?]]
   [:lower {:optional true} [:maybe int?]]
   [:value {:optional true} [:maybe int?]]
   [:default {:optional true} [:maybe int?]]
   [:reason {:optional true} [:maybe string?]]])

(def PolicyList [:vector [:ref "Policy"]])

(def LimitList [:vector [:ref "Limit"]])

(def registry
  (components-registry [#'PolicyCapability #'PolicyEffect #'LimitType
                        #'LimitKind #'Policy #'Limit #'PolicyList #'LimitList]))
