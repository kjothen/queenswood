(ns com.repldriven.mono.bank-api.payment.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private outbound-payment-status-enum
  (coercion/enum-coercion {"pending" :outbound-payment-status-pending
                           "processing" :outbound-payment-status-processing
                           "completed" :outbound-payment-status-completed
                           "failed" :outbound-payment-status-failed}
                          :outbound-payment-status-unknown))

(def outbound-payment-status-enum-schema
  (:enum-schema outbound-payment-status-enum))
