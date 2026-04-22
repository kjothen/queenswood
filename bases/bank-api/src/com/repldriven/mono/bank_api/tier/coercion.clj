(ns com.repldriven.mono.bank-api.tier.coercion
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

(def policy-capability-enum-schema (:enum-schema policy-capability-enum))

(def ^:private policy-effect-enum
  (coercion/enum-coercion {"allow" :policy-effect-allow
                           "deny" :policy-effect-deny}
                          :policy-effect-unknown))

(def policy-effect-enum-schema (:enum-schema policy-effect-enum))

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

(def limit-type-enum-schema (:enum-schema limit-type-enum))

(def ^:private product-type-enum
  (coercion/enum-coercion {"internal" :product-type-internal
                           "settlement" :product-type-settlement
                           "current" :product-type-current
                           "savings" :product-type-savings
                           "term-deposit" :product-type-term-deposit}
                          :product-type-unknown))

(def ^:private balance-type-enum
  (coercion/enum-coercion {"default" :balance-type-default
                           "suspense" :balance-type-suspense
                           "interest-payable" :balance-type-interest-payable
                           "interest-receivable"
                           :balance-type-interest-receivable}
                          :balance-type-unknown))

(def ^:private organization-type-enum
  (coercion/enum-coercion {"internal" :organization-type-internal
                           "customer" :organization-type-customer}
                          :organization-type-unknown))

(def ^:private limit-kind-decoders
  {:product-type (:decode product-type-enum)
   :balance-type (:decode balance-type-enum)
   :organization-type (:decode organization-type-enum)})

(def ^:private limit-kind-encoders
  {:product-type (:encode product-type-enum)
   :balance-type (:encode balance-type-enum)
   :organization-type (:encode organization-type-enum)})

(defn decode-limit-kind
  [kind]
  (if (map? kind)
    (reduce-kv (fn [acc k v]
                 (assoc acc
                        k
                        (if-let [decoder (get limit-kind-decoders k)]
                          (decoder v)
                          v)))
               {}
               kind)
    kind))

(defn encode-limit-kind
  [kind]
  (if (map? kind)
    (reduce-kv (fn [acc k v]
                 (assoc acc
                        k
                        (if-let [encoder (get limit-kind-encoders k)]
                          (encoder v)
                          v)))
               {}
               kind)
    kind))
