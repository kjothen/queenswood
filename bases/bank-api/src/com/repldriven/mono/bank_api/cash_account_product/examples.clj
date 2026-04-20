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

(def registry
  (examples-registry [#'ProductNotFound #'VersionNotFound #'NoPublishedVersion
                      #'NoDraft]))

(def CashAccountProductVersion
  {:organization-id "org_01JMABC"
   :product-id "prd_01JMABC123"
   :version-id "prv_01JMABC456"
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
   :valid-to "2025-12-31"
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
   :valid-from "2025-01-01"
   :valid-to "2025-12-31"})
