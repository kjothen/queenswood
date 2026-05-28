(ns com.repldriven.mono.bank-api.bank.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private bank-type-enum
  (coercion/enum-coercion {"internal" :bank-type-internal
                           "customer" :bank-type-customer}
                          :bank-type-unknown))

(def bank-type-enum-schema (:enum-schema bank-type-enum))

(def ^:private bank-status-enum
  (coercion/enum-coercion {"test" :bank-status-test "live" :bank-status-live}
                          :bank-status-unknown))

(def bank-status-enum-schema (:enum-schema bank-status-enum))
