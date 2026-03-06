(ns com.repldriven.mono.bank-api.accounts.components
  (:require
    [com.repldriven.mono.bank-api.accounts.examples :as examples]

    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def AccountId
  [:string {:title "AccountId" :json-schema/example examples/AccountId}])

(def Account
  [:map
   {:json-schema/example examples/Account}
   [:account-id [:ref "AccountId"]]
   [:customer-id string?]
   [:name string?]
   [:currency string?]
   [:status string?]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def CreateAccountRequest
  [:map
   {:json-schema/example examples/CreateAccountRequest}
   [:customer-id string?]
   [:name string?]
   [:currency string?]])

(def CreateAccountResponse [:ref "Account"])

(def AccountList
  [:map
   {:json-schema/example examples/AccountList}
   [:accounts
    [:vector [:ref "Account"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def CloseAccountResponse [:ref "Account"])

(def registry
  (components-registry [#'AccountId #'Account #'CreateAccountRequest
                        #'CreateAccountResponse #'AccountList
                        #'CloseAccountResponse]))

