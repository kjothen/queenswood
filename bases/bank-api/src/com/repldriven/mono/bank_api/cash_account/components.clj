(ns com.repldriven.mono.bank-api.cash-account.components
  (:require
    [com.repldriven.mono.bank-api.cash-account.coercion :as coercion]
    [com.repldriven.mono.bank-api.cash-account.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def CashAccountId
  (schema/id-schema "CashAccountId" "acc" examples/CashAccountId))

(def ScanAddress
  [:map
   [:sort-code [:ref "SortCode"]]
   [:account-number [:ref "AccountNumber"]]])

(def PaymentAddress
  [:map [:scheme [:ref "PaymentAddressScheme"]]
   [:identifier {:optional true}
    [:maybe
     [:map
      [:scan {:optional true} [:maybe [:ref "ScanAddress"]]]
      [:value {:optional true} [:maybe string?]]]]]])

(def CashAccountStatus
  (coercion/cash-account-status-enum-schema {:json-schema/example "opened"}))

(def AccountType
  (coercion/account-type-enum-schema {:json-schema/example "personal"}))

(def CashAccount
  [:map {:json-schema/example examples/CashAccount}
   [:organization-id [:ref "OrganizationId"]]
   [:account-id [:ref "CashAccountId"]]
   [:party-id [:ref "PartyId"]]
   [:name [:ref "Name"]]
   [:currency [:ref "Currency"]]
   [:product-id [:ref "ProductId"]]
   [:version-id [:ref "VersionId"]]
   [:product-type [:ref "ProductType"]]
   [:account-type [:ref "AccountType"]]
   [:account-status [:ref "CashAccountStatus"]]
   [:payment-addresses [:vector [:ref "PaymentAddress"]]]
   [:balances {:optional true} [:vector [:ref "Balance"]]]
   [:posted-balance {:optional true} [:ref "SignedAmount"]]
   [:available-balance {:optional true} [:ref "SignedAmount"]]
   [:transactions {:optional true} [:vector [:ref "Transaction"]]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def CreateCashAccountRequest
  [:map {:json-schema/example examples/CreateCashAccountRequest}
   [:party-id [:ref "PartyId"]]
   [:name [:ref "Name"]]
   [:currency [:ref "Currency"]]
   [:product-id [:ref "ProductId"]]])

(def CreateCashAccountResponse [:ref "CashAccount"])

(def CashAccountList
  [:map {:json-schema/example examples/CashAccountList}
   [:cash-accounts [:vector [:ref "CashAccount"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def CloseCashAccountResponse [:ref "CashAccount"])

(def registry
  (components-registry [#'CashAccountId #'ScanAddress #'PaymentAddress
                        #'CashAccountStatus #'AccountType #'CashAccount
                        #'CreateCashAccountRequest #'CreateCashAccountResponse
                        #'CashAccountList #'CloseCashAccountResponse]))
