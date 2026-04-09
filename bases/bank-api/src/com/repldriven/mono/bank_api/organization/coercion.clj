(ns com.repldriven.mono.bank-api.organization.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private organization-type-enum
  (coercion/enum-coercion {"internal" :organization-type-internal
                           "customer" :organization-type-customer}
                          :organization-type-unknown))

(def organization-type-enum-schema (:enum-schema organization-type-enum))
