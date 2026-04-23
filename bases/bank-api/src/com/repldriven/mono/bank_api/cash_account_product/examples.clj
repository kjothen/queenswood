(ns com.repldriven.mono.bank-api.cash-account-product.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def ProductNotFound
  {:value {:title "REJECTED"
           :type "cash-account-products/not-found"
           :status 404
           :detail "Product not found"}})

(def VersionNotFound
  {:value {:title "REJECTED"
           :type "cash-account-products/version-not-found"
           :status 404
           :detail "Version not found"}})

(def NoPublishedVersion
  {:value {:title "REJECTED"
           :type "cash-account-products/no-published-version"
           :status 404
           :detail "No published version found"}})

(def NoDraft
  {:value {:title "REJECTED"
           :type "cash-account-products/no-draft"
           :status 409
           :detail "No draft to publish"}})

(def DuplicateItems
  {:value {:title "REJECTED"
           :type ":cash-account-product/duplicate-items"
           :status 409
           :detail "Duplicate items in: balance-products"}})

(def registry
  (examples-registry [#'ProductNotFound #'VersionNotFound #'NoPublishedVersion
                      #'NoDraft #'DuplicateItems]))

(def ProductId "prd.01kprbmgcj35ptc8npmybhh4se")
(def VersionId "prv.01kprbmgcj35ptc8npmybhh4sf")

(def CashAccountProductVersion
  {:organization-id "org.01kprbmgcj35ptc8npmybhh4s7"
   :product-id ProductId
   :version-id VersionId
   :version-number 1
   :status :draft
   :name "Current Account"
   :product-type :current
   :balance-sheet-side :liability
   :allowed-currencies ["GBP" "EUR"]
   :balance-products [{:balance-type :default :balance-status :posted}]
   :allowed-payment-address-schemes [:scan]
   :interest-rate-bps 0
   :valid-from "2025-01-01"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def CashAccountProductVersionList {:versions [CashAccountProductVersion]})

(def DraftCashAccountProductRequest
  {:name "Current Account"
   :product-type :current
   :balance-sheet-side :liability
   :allowed-currencies ["GBP" "EUR"]
   :balance-products [{:balance-type :default :balance-status :posted}]
   :allowed-payment-address-schemes [:scan]
   :interest-rate-bps 0
   :valid-from "2025-01-01"})
