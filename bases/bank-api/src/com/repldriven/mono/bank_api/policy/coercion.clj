(ns com.repldriven.mono.bank-api.policy.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private policy-category-enum
  (coercion/enum-coercion {"standard" :policy-category-standard
                           "restricted" :policy-category-restricted
                           "emergency" :policy-category-emergency}
                          :policy-category-unknown))

(def policy-category-enum-schema (:enum-schema policy-category-enum))
