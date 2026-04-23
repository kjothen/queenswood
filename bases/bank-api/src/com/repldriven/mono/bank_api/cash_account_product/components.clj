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

(def CashAccountProductRequest
  [:map {:closed true :json-schema/example examples/CashAccountProductRequest}
   [:name [:ref "Name"]]
   [:product-type [:ref "ProductType"]]
   [:balance-sheet-side [:ref "BalanceSheetSide"]]
   [:allowed-currencies [:unique-vector {:min 1} [:ref "Currency"]]]
   [:balance-products [:unique-vector {:min 1} [:ref "BalanceProduct"]]]
   [:allowed-payment-address-schemes
    [:unique-vector {:min 1} [:ref "PaymentAddressScheme"]]]
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
   [:allowed-currencies [:unique-vector-lax {:min 1} [:ref "Currency"]]]
   [:balance-products [:unique-vector-lax {:min 1} [:ref "BalanceProduct"]]]
   [:allowed-payment-address-schemes
    [:unique-vector-lax {:min 1} [:ref "PaymentAddressScheme"]]]
   [:interest-rate-bps {:optional true} [:ref "SignedBasisPoints"]]
   [:valid-from {:optional true} [:maybe [:ref "Date"]]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]
   [:discarded-at {:optional true} [:ref "Timestamp"]]])

(def CashAccountProduct
  [:map {:json-schema/example examples/CashAccountProduct}
   [:product-id [:ref "ProductId"]]
   [:versions [:vector [:ref "CashAccountProductVersion"]]]])

(def CashAccountProductListLinks
  [:map
   [:next {:optional true} string?]
   [:prev {:optional true} string?]])

(def CashAccountProductList
  [:map {:json-schema/example examples/CashAccountProductList}
   [:items [:vector [:ref "CashAccountProduct"]]]
   [:links {:optional true} [:ref "CashAccountProductListLinks"]]])

(def registry
  (components-registry [#'ProductId #'VersionId #'ProductType #'BalanceSheetSide
                        #'PaymentAddressScheme #'VersionStatus
                        #'CashAccountProductRequest #'CashAccountProductVersion
                        #'CashAccountProduct #'CashAccountProductListLinks
                        #'CashAccountProductList]))
