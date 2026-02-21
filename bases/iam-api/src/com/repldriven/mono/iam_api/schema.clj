(ns com.repldriven.mono.iam-api.schema)

(def ProjectId
  [:re
   {:title "ProjectId"
    :description
    "6 to 30 lowercase letters, digits, or hyphens. Must start with a
    letter. No trailing hyphens."}
   "^[a-z][-a-z0-9]{4,28}[a-z0-9]$"])

(def UniqueId
  [:re {:title "UniqueId" :description "21-digit numeric identifier"}
   "^\\d{21}$"])

(def ServiceAccountEmail
  [:re
   {:title "ServiceAccountEmail"
    :description "RFC 5322 email address, max 320 characters"}
   "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}$"])

(def ServiceAccountName
  [:re
   {:title "ServiceAccountName"
    :description
    "`projects/{PROJECT_ID}/serviceAccounts/{EMAIL_ADDRESS}` or
    `projects/{PROJECT_ID}/serviceAccounts/{UNIQUE_ID}`"}
   "^projects/[a-z][-a-z0-9]{4,28}[a-z0-9]/serviceAccounts/(?:[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}|\\d{21})$"])

(def EmailAddressOrUniqueId
  [:re
   {:title "EmailAddressOrUniqueId"
    :description "Email address or 21-digit unique ID"}
   "^(?:[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}|\\d{21})$"])

(def ServiceAccount
  [:map [:name #'ServiceAccountName] [:project-id #'ProjectId]
   [:unique-id #'UniqueId] [:email #'ServiceAccountEmail]
   [:display-name [:string {:max 100}]] [:description [:string {:max 256}]]
   [:disabled boolean?]])

(def ServiceAccountInput
  [:map ["display-name" [:string {:max 100}]]
   ["description" [:string {:max 256}]]])

(def ServiceAccountCreateBody
  [:map ["account-id" #'ProjectId] ["service-account" #'ServiceAccountInput]])

(def ServiceAccountPatchBody [:map ["service-account" #'ServiceAccountInput]])
