(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:require
    [com.repldriven.mono.iam-api.v1.projects.service-accounts.handlers :as
     handlers]))

(defn routes
  []
  [["/serviceAccounts"
    {:get {:summary
           "Lists every ServiceAccount that belongs to a specific project"
           :openapi {:operationId "ListServiceAccounts"}
           :responses {200 {:body [:map
                                   [:accounts
                                    [:vector [:ref "ServiceAccount"]]]]}}
           :handler handlers/list}
     :post {:summary "Creates a ServiceAccount"
            :openapi {:operationId "CreateServiceAccount"}
            :parameters {:body [:ref "CreateServiceAccountRequest"]}
            :responses {201 {:body [:ref "ServiceAccount"]}}
            :handler handlers/create}}]
   ["/serviceAccounts/{email-or-unique-id}"
    {:parameters {:path {:email-or-unique-id [:or [:ref "Email"]
                                              [:ref "UniqueId"]]}}
     :get {:summary "Gets a ServiceAccount"
           :openapi {:operationId "GetServiceAccount"}
           :responses {200 {:body [:ref "ServiceAccount"]}}
           :handler handlers/get}
     :patch {:summary "Patches a ServiceAccount"
             :openapi {:operationId "PatchServiceAccount"}
             :parameters {:body [:ref "PatchServiceAccountRequest"]}
             :responses {200 {:body [:ref "ServiceAccount"]}}
             :handler handlers/patch}
     :delete {:summary "Deletes a ServiceAccount"
              :openapi {:operationId "DeleteServiceAccount"}
              :responses {200 {:body [:ref "ServiceAccount"]}}
              :handler handlers/delete}}]
   ["/serviceAccounts/{email-or-unique-id}:undelete"
    {:parameters {:path {:email-or-unique-id [:or [:ref "Email"]
                                              [:ref "UniqueId"]]}}
     :post {:summary "Undeletes a ServiceAccount that was deleted"
            :openapi {:operationId "UndeleteServiceAccount"}
            :responses {200 {:body [:ref "ServiceAccount"]}}
            :handler handlers/undelete}}]
   ["/serviceAccounts/{email-or-unique-id}:enable"
    {:parameters {:path {:email-or-unique-id [:or [:ref "Email"]
                                              [:ref "UniqueId"]]}}
     :post {:summary "Enables a ServiceAccount that was disabled"
            :openapi {:operationId "EnableServiceAccount"}
            :responses {200 {:body [:ref "ServiceAccount"]}}
            :handler handlers/enable}}]
   ["/serviceAccounts/{email-or-unique-id}:disable"
    {:parameters {:path {:email-or-unique-id [:or [:ref "Email"]
                                              [:ref "UniqueId"]]}}
     :post {:summary "Disables a ServiceAccount immediately"
            :openapi {:operationId "DisableServiceAccount"}
            :responses {200 {:body [:ref "ServiceAccount"]}}
            :handler handlers/disable}}]])

(comment
  (require '[reitit.core :as r])
  (-> (r/router ["http://localhost/{projects/*}/serviceAccounts" ::user-by-id]
                {:syntax :bracket})
      (r/match-by-path "http://localhost/projects/prj-123/serviceAccounts")))
