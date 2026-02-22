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
    letter. No trailing hyphens."
    :json-schema/example "my-project"} (str "^" project-id-pat "$")])

(def ServiceAccountId
  [:re
   {:title "ServiceAccountId"
    :description
    "6 to 30 lowercase letters, digits, or hyphens. Must start with a
    letter. No trailing hyphens."
    :json-schema/example "my-service-account"} (str "^" project-id-pat "$")])

(def UniqueId
  [:re
   {:title "UniqueId"
    :description "21-digit numeric identifier"
    :json-schema/example "103798426813399185444"} (str "^" unique-id-pat "$")])

(def ServiceAccountEmail
  [:re
   {:title "ServiceAccountEmail"
    :description "RFC 5322 email address, max 320 characters"
    :json-schema/example "my-service-account@my-project.iam.repldriven.com"}
   (str "^" email-pat "$")])

(def ServiceAccountName
  [:re
   {:title "ServiceAccountName"
    :description "`projects/{PROJECT_ID}/serviceAccounts/{EMAIL_ADDRESS}`"
    :json-schema/example
    "projects/my-project/serviceAccounts/my-service-account@my-project.iam.repldriven.com"}
   (str "^projects/" project-id-pat "/serviceAccounts/" email-pat "$")])

(def ServiceAccountIdentifier
  [:or [:ref "ServiceAccountEmail"] [:ref "UniqueId"]])

(def ServiceAccount
  [:map [:name [:ref "ServiceAccountName"]] [:project-id [:ref "ProjectId"]]
   [:unique-id [:ref "UniqueId"]] [:email [:ref "ServiceAccountEmail"]]
   [:display-name [:string {:max 100}]] [:description [:string {:max 256}]]
   [:disabled boolean?]])

(def CreateServiceAccountRequest
  [:map ["account-id" [:ref "ServiceAccountId"]]
   ["display-name"
    [:string {:max 100 :json-schema/example "My Service Account"}]]
   ["description"
    [:string
     {:max 256 :json-schema/example "A service account for automated tasks"}]]])

(def PatchServiceAccountRequest
  [:map
   ["display-name"
    [:string {:max 100 :json-schema/example "My Service Account"}]]
   ["description"
    [:string
     {:max 256 :json-schema/example "A service account for automated tasks"}]]])

(def registry
  (array-map "CreateServiceAccountRequest" CreateServiceAccountRequest
             "PatchServiceAccountRequest" PatchServiceAccountRequest
             "ServiceAccount" ServiceAccount
             "ServiceAccountEmail" ServiceAccountEmail
             "ServiceAccountId" ServiceAccountId
             "ServiceAccountName" ServiceAccountName
             "ServiceAccountIdentifier" ServiceAccountIdentifier
             "ProjectId" ProjectId
             "UniqueId" UniqueId))
