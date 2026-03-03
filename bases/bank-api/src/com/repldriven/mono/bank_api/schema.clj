(ns com.repldriven.mono.bank-api.schema)

(def AccountId
  [:string {:title "AccountId" :json-schema/example "acc_01JM..."}])

(def OpenAccountRequest
  [:map [:customer-id string?] [:name string?] [:currency string?]])

(def Account
  [:map [:account-id [:ref "AccountId"]] [:customer-id string?] [:name string?]
   [:currency string?] [:status string?]
   [:created-at-ms {:optional true} [:maybe string?]]
   [:updated-at-ms {:optional true} [:maybe string?]]])

(def AccountList
  [:map [:accounts [:vector [:ref "Account"]]]
   [:links {:optional true}
    [:map [:next {:optional true} string?] [:prev {:optional true} string?]]]])

(def CloseAccountResponse [:ref "Account"])

(def CreateOrganizationRequest [:map [:name string?]])

(def Organization
  [:map [:organization-id string?] [:name string?] [:status string?]])

(def ApiKeyResponse
  [:map [:id string?] [:key-prefix string?] [:raw-key string?]])

(def CreateOrganizationResponse
  [:map [:organization [:ref "Organization"]]
   [:api-key [:ref "ApiKeyResponse"]]])

(def ErrorResponse [:map [:error string?]])

(def registry
  (array-map "AccountId" AccountId
             "OpenAccountRequest" OpenAccountRequest
             "Account" Account
             "AccountList" AccountList
             "CloseAccountResponse" CloseAccountResponse
             "CreateOrganizationRequest" CreateOrganizationRequest
             "Organization" Organization
             "ApiKeyResponse" ApiKeyResponse
             "CreateOrganizationResponse" CreateOrganizationResponse
             "ErrorResponse" ErrorResponse))
