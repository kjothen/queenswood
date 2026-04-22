(ns com.repldriven.mono.bank-api.organization.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private organization-type-enum
  (coercion/enum-coercion {"internal" :organization-type-internal
                           "customer" :organization-type-customer}
                          :organization-type-unknown))

(def organization-type-enum-schema (:enum-schema organization-type-enum))

(def ^:private organization-status-enum
  (coercion/enum-coercion {"test" :organization-status-test
                           "live" :organization-status-live}
                          :organization-status-unknown))

(def organization-status-enum-schema (:enum-schema organization-status-enum))
