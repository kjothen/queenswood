(ns com.repldriven.mono.bank-api.cash-account.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def CashAccountNotFound
  {:value {:title "REJECTED"
           :type "cash-accounts/not-found"
           :status 404
           :detail "Cash account not found"}})

(def CashAccountAlreadyExists
  {:value {:title "REJECTED"
           :type "cash-accounts/exists"
           :status 422
           :detail "Customer already has an account of this kind"}})

(def ProductNotPublished
  {:value {:title "REJECTED"
           :type "cash-account/product-not-published"
           :status 422
           :detail "No published product version found"}})

(def InvalidCurrency
  {:value {:title "REJECTED"
           :type "cash-account/invalid-currency"
           :status 422
           :detail "Currency not allowed for this product"}})

(def PartyNotFound
  {:value {:title "REJECTED"
           :type ":party/not-found"
           :status 404
           :detail "Party not found"}})

(def ProductNotFound
  {:value {:title "REJECTED"
           :type ":cash-account-product/not-found"
           :status 404
           :detail "Product not found"}})

(def registry
  (examples-registry [#'CashAccountNotFound #'CashAccountAlreadyExists
                      #'ProductNotPublished #'InvalidCurrency #'PartyNotFound
                      #'ProductNotFound]))

(def CashAccount
  {:organization-id "org.01kprbmgcj35ptc8npmybhh4s7"
   :account-id "acc.01kprbmgcj35ptc8npmybhh4s8"
   :party-id "pty.01kprbmgcj35ptc8npmybhh4s9"
   :name "Arthur Phillip Dent - Current Account"
   :currency "GBP"
   :product-id "prd.01kprbmgcj35ptc8npmybhh4se"
   :version-id "prv.01kprbmgcj35ptc8npmybhh4sf"
   :product-type :current
   :account-type :personal
   :account-status :opened
   :payment-addresses [{:scheme :scan
                        :identifier {:scan {:sort-code "040004"
                                            :account-number "12345678"}}}]})

(def CashAccountId (:account-id CashAccount))

(def CashAccountList {:cash-accounts [CashAccount]})

(def CreateCashAccountRequest
  (select-keys CashAccount [:party-id :name :currency :product-id]))

(def CreateCashAccountResponse CashAccount)

(def CloseCashAccountResponse CashAccount)
