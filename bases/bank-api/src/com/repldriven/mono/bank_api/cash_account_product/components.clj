(ns com.repldriven.mono.bank-api.cash-account-product.components
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.coercion :as
     coercion]
    [com.repldriven.mono.bank-api.cash-account-product.examples :as
     examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def ProductType
  (coercion/product-type-enum-schema {:json-schema/example "current"}))

(def BalanceSheetSide
  (coercion/balance-sheet-side-enum-schema {:json-schema/example "liability"}))

(def PaymentAddressScheme
  (coercion/payment-address-scheme-enum-schema {:json-schema/example "scan"}))

(def VersionStatus
  (coercion/version-status-enum-schema {:json-schema/example "draft"}))

(def DraftCashAccountProductRequest
  [:map {:json-schema/example examples/DraftCashAccountProductRequest}
   [:name string?]
   [:product-type [:ref "ProductType"]]
   [:balance-sheet-side [:ref "BalanceSheetSide"]]
   [:allowed-currencies {:optional true} [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProductRequest"]]]]
   [:allowed-payment-address-schemes {:optional true}
    [:maybe [:vector [:ref "PaymentAddressScheme"]]]]
   [:interest-rate-bps {:optional true} [:maybe int?]]
   [:valid-from {:optional true} [:maybe [:ref "Timestamp"]]]
   [:valid-to {:optional true} [:maybe [:ref "Timestamp"]]]])

(def CashAccountProductVersion
  [:map {:json-schema/example examples/CashAccountProductVersion}
   [:organization-id string?]
   [:product-id string?]
   [:version-id string?]
   [:version-number int?]
   [:status [:ref "VersionStatus"]]
   [:name {:optional true} [:maybe string?]]
   [:product-type {:optional true} [:maybe [:ref "ProductType"]]]
   [:balance-sheet-side {:optional true} [:maybe [:ref "BalanceSheetSide"]]]
   [:allowed-currencies {:optional true} [:maybe [:vector [:ref "Currency"]]]]
   [:balance-products {:optional true}
    [:maybe [:vector [:ref "BalanceProduct"]]]]
   [:allowed-payment-address-schemes {:optional true}
    [:maybe [:vector [:ref "PaymentAddressScheme"]]]]
   [:interest-rate-bps {:optional true} [:maybe int?]]
   [:valid-from {:optional true} [:maybe string?]]
   [:valid-to {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def CashAccountProductVersionList
  [:map {:json-schema/example examples/CashAccountProductVersionList}
   [:versions [:vector [:ref "CashAccountProductVersion"]]]])

(def registry
  (components-registry [#'ProductType #'BalanceSheetSide #'PaymentAddressScheme
                        #'VersionStatus #'DraftCashAccountProductRequest
                        #'CashAccountProductVersion
                        #'CashAccountProductVersionList]))
