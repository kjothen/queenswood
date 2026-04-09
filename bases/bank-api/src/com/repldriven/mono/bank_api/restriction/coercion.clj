(ns com.repldriven.mono.bank-api.restriction.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private policy-capability-enum
  (coercion/enum-coercion
   {"account-opening" :policy-capability-account-opening
    "account-closure" :policy-capability-account-closure
    "internal-payments" :policy-capability-internal-payments
    "inbound-payments" :policy-capability-inbound-payments
    "outbound-payments" :policy-capability-outbound-payments}
   :policy-capability-unknown))

(def ^:private policy-effect-enum
  (coercion/enum-coercion {"allow" :policy-effect-allow
                           "deny" :policy-effect-deny}
                          :policy-effect-unknown))

(def ^:private limit-type-enum
  (coercion/enum-coercion
   {"max-inbound-payment-amount" :limit-type-max-inbound-payment-amount
    "max-outbound-payment-amount" :limit-type-max-outbound-payment-amount
    "max-internal-payment-amount" :limit-type-max-internal-payment-amount
    "min-inbound-payment-amount" :limit-type-min-inbound-payment-amount
    "min-outbound-payment-amount" :limit-type-min-outbound-payment-amount
    "min-internal-payment-amount" :limit-type-min-internal-payment-amount
    "max-inbound-payment-daily-amount"
    :limit-type-max-inbound-payment-daily-amount
    "max-outbound-payment-daily-amount"
    :limit-type-max-outbound-payment-daily-amount
    "max-internal-payment-daily-amount"
    :limit-type-max-internal-payment-daily-amount
    "max-accounts" :limit-type-max-accounts
    "max-accounts-per-party" :limit-type-max-accounts-per-party
    "min-balance" :limit-type-min-balance
    "max-balance" :limit-type-max-balance
    "max-organizations" :limit-type-max-organizations}
   :limit-type-unknown))

(def policy-capability-enum-schema (:enum-schema policy-capability-enum))

(def policy-effect-enum-schema (:enum-schema policy-effect-enum))

(def limit-type-enum-schema (:enum-schema limit-type-enum))
