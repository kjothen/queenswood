(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:require
    [com.repldriven.mono.iam-api.v1.projects.service-accounts.handlers :as
     handlers]))

(def ^:private email-address-or-unique-id
  [:re {:error/message "must be an `{EMAIL_ADDRESS}` or `{UNIQUE_ID}`"}
   "^(?:[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}|\\d{21})$"])

(def ^:private service-account
  [:map
   [:name
    [:re
     {:error/message
      "must be `projects/{PROJECT_ID}/serviceAccounts/{EMAIL_ADDRESS}` or `projects/{PROJECT_ID}/serviceAccounts/{UNIQUE_ID}`"}
     "^projects/[a-z][-a-z0-9]{4,28}[a-z0-9]/serviceAccounts/(?:[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}|\\d{21})$"]]
   [:project-id
    [:re
     {:error/message
      "must be 6 to 30 lowercase letters, digits, or hyphen. Must start with a letter. Trailing hyphens are prohibited"}
     "^[a-z][-a-z0-9]{4,28}[a-z0-9]$"]]
   [:unique-id [:re {:error/message "must be 21 digits"} "^\\d{21}$"]]
   [:email
    [:re {:max 320 :error/message "must be a valid RFC 5322 email address"}
     "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]{1,64}@[a-zA-Z0-9.-]{1,255}$"]]
   [:display-name
    [:string {:max 100 :error/message "must be maximum of 100 UTF-8 bytes"}]]
   [:description
    [:string {:max 256 :error/message "must be maximum of 256 UTF-8 bytes"}]]
   [:disabled boolean?]])

(def ^:private service-account-create-body
  [:map
   ["account-id"
    [:re
     {:error/message
      "must be 6 to 30 lowercase letters, digits, or hyphen. Must start with a letter. Trailing hyphens are prohibited"}
     "^[a-z][-a-z0-9]{4,28}[a-z0-9]$"]]
   ["service-account"
    [:map
     ["display-name"
      [:string {:max 100 :error/message "must be maximum of 100 UTF-8 bytes"}]]
     ["description"
      [:string
       {:max 256 :error/message "must be maximum of 256 UTF-8 bytes"}]]]]])

(def ^:private service-account-patch-body
  [:map
   ["service-account"
    [:map
     ["display-name"
      [:string {:max 100 :error/message "must be maximum of 100 UTF-8 bytes"}]]
     ["description"
      [:string
       {:max 256 :error/message "must be maximum of 256 UTF-8 bytes"}]]]]])

(defn routes
  []
  [["/serviceAccounts"
    {:get {:summary
           "Lists every ServiceAccount that belongs to a specific project"
           :responses {200 {:body [:map [:accounts [:vector service-account]]]}}
           :handler handlers/list}
     :post {:summary "Creates a ServiceAccount"
            :parameters {:body service-account-create-body}
            :responses {201 {:body service-account}}
            :handler handlers/create}}]
   ["/serviceAccounts/{email-or-unique-id}"
    {:parameters {:path {:email-or-unique-id email-address-or-unique-id}}
     :get {:summary "Gets a ServiceAccount"
           :responses {200 {:body service-account}}
           :handler handlers/get}
     :patch {:summary "Patches a ServiceAccount"
             :parameters {:body service-account-patch-body}
             :responses {200 {:body service-account}}
             :handler handlers/patch}
     :delete {:summary "Deletes a ServiceAccount"
              :responses {204 {}}
              :handler handlers/delete}}]
   ["/serviceAccounts/{email-or-unique-id}:undelete"
    {:parameters {:path {:email-or-unique-id email-address-or-unique-id}}
     :post {:summary "Undeletes a ServiceAccount that was deleted"
            :responses {204 {}}
            :handler handlers/undelete}}]
   ["/serviceAccounts/{email-or-unique-id}:enable"
    {:parameters {:path {:email-or-unique-id string?}}
     :post {:summary "Enables a ServiceAccount that was disabled"
            :responses {204 {}}
            :handler handlers/enable}}]
   ["/serviceAccounts/{email-or-unique-id}:disable"
    {:parameters {:path {:email-or-unique-id string?}}
     :post {:summary "Disables a ServiceAccount immediately"
            :responses {204 {}}
            :handler handlers/disable}}]])

(comment
  (require '[reitit.core :as r])
  (-> (r/router ["http://localhost/{projects/*}/serviceAccounts" ::user-by-id]
                {:syntax :bracket})
      (r/match-by-path "http://localhost/projects/prj-123/serviceAccounts")))
