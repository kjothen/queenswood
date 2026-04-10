(ns com.repldriven.mono.bank-api.organization.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private organization-type-enum
  (coercion/enum-coercion {"internal" :organization-type-internal
                           "customer" :organization-type-customer}
                          :organization-type-unknown))

(def organization-type-enum-schema (:enum-schema organization-type-enum))

(def ^:private tier-type-enum
  (coercion/enum-coercion {"system" :tier-type-system
                           "micro" :tier-type-micro
                           "standard" :tier-type-standard}
                          :tier-type-unknown))

(def tier-type-enum-schema (:enum-schema tier-type-enum))

(def ^:private customer-tier-type-enum
  (coercion/enum-coercion {"micro" :tier-type-micro
                           "standard" :tier-type-standard}
                          :tier-type-unknown))

(def customer-tier-type-enum-schema (:enum-schema customer-tier-type-enum))
