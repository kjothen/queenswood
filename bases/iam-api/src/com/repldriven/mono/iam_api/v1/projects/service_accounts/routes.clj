(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:require
    [com.repldriven.mono.iam-api.v1.projects.service-accounts.handlers :as
     handlers]

    [com.repldriven.mono.iam.interface :as iam]))

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
    {:get
     {:summary "Lists every ServiceAccount that belongs to a specific project"
      :responses {200 {:body [:map [:accounts [:vector iam/ServiceAccount]]]}}
      :handler handlers/list}
     :post {:summary "Creates a ServiceAccount"
            :parameters {:body service-account-create-body}
            :responses {201 {:body iam/ServiceAccount}}
            :handler handlers/create}}]
   ["/serviceAccounts/{email-or-unique-id}"
    {:parameters {:path {:email-or-unique-id iam/EmailAddressOrUniqueId}}
     :get {:summary "Gets a ServiceAccount"
           :responses {200 {:body iam/ServiceAccount}}
           :handler handlers/get}
     :patch {:summary "Patches a ServiceAccount"
             :parameters {:body service-account-patch-body}
             :responses {200 {:body iam/ServiceAccount}}
             :handler handlers/patch}
     :delete {:summary "Deletes a ServiceAccount"
              :responses {204 {}}
              :handler handlers/delete}}]
   ["/serviceAccounts/{email-or-unique-id}:undelete"
    {:parameters {:path {:email-or-unique-id iam/EmailAddressOrUniqueId}}
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
