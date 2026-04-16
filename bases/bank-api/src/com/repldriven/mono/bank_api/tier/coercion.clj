(ns com.repldriven.mono.bank-api.tier.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private tier-type-enum
  (coercion/enum-coercion {"system" :tier-type-system
                           "micro" :tier-type-micro
                           "standard" :tier-type-standard}
                          :tier-type-unknown))

(def tier-type-enum-schema (:enum-schema tier-type-enum))

(def ^:private policy-capability-enum
  (coercion/enum-coercion
   {"account-opening" :policy-capability-account-opening
    "account-closure" :policy-capability-account-closure
    "internal-payments" :policy-capability-internal-payments
    "inbound-payments" :policy-capability-inbound-payments
    "outbound-payments" :policy-capability-outbound-payments}
   :policy-capability-unknown))

(def policy-capability-enum-schema (:enum-schema policy-capability-enum))

(def ^:private policy-effect-enum
  (coercion/enum-coercion {"allow" :policy-effect-allow
                           "deny" :policy-effect-deny}
                          :policy-effect-unknown))

(def policy-effect-enum-schema (:enum-schema policy-effect-enum))
