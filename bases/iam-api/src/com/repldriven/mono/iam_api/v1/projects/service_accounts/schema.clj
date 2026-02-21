(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.schema)

(def ^:private project-id-pat "[a-z][-a-z0-9]{4,28}[a-z0-9]")
(def ^:private email-pat
  "[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}")
(def ^:private unique-id-pat "\\d{21}")

(def ProjectId
  [:re
   {:title "ProjectId"
    :description
    "6 to 30 lowercase letters, digits, or hyphens. Must start with a
    letter. No trailing hyphens."}
   (str "^" project-id-pat "$")])

(def UniqueId
  [:re {:title "UniqueId" :description "21-digit numeric identifier"}
   (str "^" unique-id-pat "$")])

(def ServiceAccountEmail
  [:re
   {:title "ServiceAccountEmail"
    :description "RFC 5322 email address, max 320 characters"}
   (str "^" email-pat "$")])

(def ServiceAccountNameByEmail
  [:re
   {:title "ServiceAccountNameByEmail"
    :description "`projects/{PROJECT_ID}/serviceAccounts/{EMAIL_ADDRESS}`"}
   (str "^projects/" project-id-pat "/serviceAccounts/" email-pat "$")])

(def ServiceAccountNameByUniqueId
  [:re
   {:title "ServiceAccountNameByUniqueId"
    :description "`projects/{PROJECT_ID}/serviceAccounts/{UNIQUE_ID}`"}
   (str "^projects/" project-id-pat "/serviceAccounts/" unique-id-pat "$")])

(def ServiceAccountName
  [:or #'ServiceAccountNameByEmail #'ServiceAccountNameByUniqueId])

(def EmailAddressOrUniqueId [:or #'ServiceAccountEmail #'UniqueId])

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
