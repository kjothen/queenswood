(ns com.repldriven.mono.bank-api.cash-account-product.components
  (:require
    [com.repldriven.mono.bank-api.cash-account-product.coercion :as
     coercion]
    [com.repldriven.mono.bank-api.cash-account-product.examples :as
     examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def ProductId (schema/id-schema "ProductId" "prd" examples/ProductId))

(def VersionId (schema/id-schema "VersionId" "prv" examples/VersionId))

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
   [:name [:ref "Name"]]
   [:product-type [:ref "ProductType"]]
   [:balance-sheet-side [:ref "BalanceSheetSide"]]
   [:allowed-currencies [:set {:min 1} [:ref "Currency"]]]
   [:balance-products [:set {:min 1} [:ref "BalanceProductRequest"]]]
   [:allowed-payment-address-schemes
    [:set {:min 1} [:ref "PaymentAddressScheme"]]]
   [:interest-rate-bps {:optional true} [:ref "SignedBasisPoints"]]
   [:valid-from {:optional true} [:ref "Date"]]])

(def CashAccountProductVersion
  [:map {:json-schema/example examples/CashAccountProductVersion}
   [:organization-id [:ref "OrganizationId"]]
   [:product-id [:ref "ProductId"]]
   [:version-id [:ref "VersionId"]]
   [:version-number int?]
   [:status [:ref "VersionStatus"]]
   [:name {:optional true} [:ref "Name"]]
   [:product-type [:ref "ProductType"]]
   [:balance-sheet-side [:ref "BalanceSheetSide"]]
   [:allowed-currencies [:set {:min 1} [:ref "Currency"]]]
   [:balance-products [:set {:min 1} [:ref "BalanceProductRequest"]]]
   [:allowed-payment-address-schemes
    [:set {:min 1} [:ref "PaymentAddressScheme"]]]
   [:interest-rate-bps {:optional true} [:ref "SignedBasisPoints"]]
   [:valid-from {:optional true} [:maybe [:ref "Date"]]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def CashAccountProductVersionList
  [:map {:json-schema/example examples/CashAccountProductVersionList}
   [:versions [:vector [:ref "CashAccountProductVersion"]]]])

(def registry
  (components-registry
   [#'ProductId #'VersionId #'ProductType #'BalanceSheetSide
    #'PaymentAddressScheme #'VersionStatus #'DraftCashAccountProductRequest
    #'CashAccountProductVersion #'CashAccountProductVersionList]))
