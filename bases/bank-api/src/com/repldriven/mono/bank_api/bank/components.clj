(ns com.repldriven.mono.bank-api.bank.components
  (:require
    [com.repldriven.mono.bank-api.bank.coercion :as coercion]
    [com.repldriven.mono.bank-api.bank.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def BankId (schema/id-schema "BankId" "bnk" examples/BankId))

(def BankStatus
  (coercion/bank-status-enum-schema {:json-schema/example "test"}))

(def CreateBankRequest
  [:map {:closed true :json-schema/example examples/CreateBankRequest}
   [:name [:ref "Name"]]
   [:status [:ref "BankStatus"]]
   [:tier [:ref "Name"]]
   [:currencies [:unique-vector {:min 1} [:ref "Currency"]]]])

(def Bank
  [:map {:json-schema/example examples/Bank}
   [:bank-id [:ref "BankId"]]
   [:name [:ref "Name"]]
   [:status [:ref "BankStatus"]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:client-id [:ref "BankId"]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def BankList
  [:map {:json-schema/example examples/BankList}
   [:banks [:vector [:ref "Bank"]]]])

(def CreateBankResponse
  [:map {:json-schema/example examples/CreateBankResponse}
   [:bank-id [:ref "BankId"]]
   [:name [:ref "Name"]]
   [:status [:ref "BankStatus"]]
   [:party [:ref "Party"]]
   [:accounts [:vector [:ref "CashAccount"]]]
   [:client-id [:ref "BankId"]]
   [:client-secret string?]
   [:created-at [:ref "Timestamp"]]
   [:updated-at [:ref "Timestamp"]]])

(def registry
  (components-registry [#'BankId #'BankStatus #'CreateBankRequest #'Bank
                        #'BankList #'CreateBankResponse]))
