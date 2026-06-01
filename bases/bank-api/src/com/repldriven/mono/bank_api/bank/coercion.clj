(ns com.repldriven.mono.bank-api.bank.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private bank-status-enum
  (coercion/enum-coercion {"test" :bank-status-test "live" :bank-status-live}
                          :bank-status-unknown))

(def bank-status-enum-schema (:enum-schema bank-status-enum))
