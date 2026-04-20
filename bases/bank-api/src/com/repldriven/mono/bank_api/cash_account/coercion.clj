(ns com.repldriven.mono.bank-api.cash-account.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private cash-account-status-enum
  (coercion/enum-coercion {"opening" :cash-account-status-opening
                           "opened" :cash-account-status-opened
                           "closing" :cash-account-status-closing
                           "closed" :cash-account-status-closed}))

(def cash-account-status-enum-schema (:enum-schema cash-account-status-enum))

(def ^:private account-type-enum
  (coercion/enum-coercion {"personal" :account-type-personal
                           "business" :account-type-business}
                          :account-type-unknown))

(def account-type-enum-schema (:enum-schema account-type-enum))
