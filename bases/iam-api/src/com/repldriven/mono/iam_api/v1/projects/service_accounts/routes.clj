(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-service-account.interface :as
             service-account]
            [malli.generator :as mg]))

;;;; Ring handlers

(def unknown-service-account-error
  {:error {:code "404" :message "Unknown service account" :status "NOT_FOUND"}})

(defn- project-name [project-id] (str "projects/" project-id))
(defn- service-account-name
  [project-id email-or-unique-id]
  (str (project-name project-id) "/serviceAccounts/" email-or-unique-id))

(defn create-service-account
  [{:keys [datasource] {:keys [body] {:keys [project-id]} :path} :parameters}]
  (log/info "create-service-account" project-id body)
  (let [result
        (service-account/create datasource (project-name project-id) body)]
    {:status 200 :body result}))

(defn get-service-account
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "get-service-account" project-id email-or-unique-id)
  (let [result (service-account/get datasource
                                    (service-account-name project-id
                                                          email-or-unique-id))]
    (if result
      {:status 200 :body result}
      {:status 404 :body unknown-service-account-error})))

(defn patch-service-account
  [{:keys [datasource]
    {:keys [body] {:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "patch-service-account" project-id email-or-unique-id body)
  (if (service-account/patch datasource
                             (service-account-name project-id
                                                   email-or-unique-id)
                             body)
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn delete-service-account
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "delete-service-account" project-id email-or-unique-id)
  (if (service-account/delete datasource
                              (service-account-name project-id
                                                    email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn undelete-service-account
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "undelete-service-account" project-id email-or-unique-id)
  (if (service-account/undelete datasource
                                (service-account-name project-id
                                                      email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn list-service-accounts
  [{:keys [datasource] {{:keys [project-id]} :path} :parameters}]
  (log/info "list-service-accounts" project-id)
  {:status 200
   :body {:accounts (service-account/list datasource
                                          (project-name project-id))}})

(defn enable-service-account
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "enable-service-account" project-id email-or-unique-id)
  (if (service-account/enable datasource
                              (service-account-name project-id
                                                    email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn disable-service-account
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "disable-service-account" project-id email-or-unique-id)
  (if (service-account/disable datasource
                               (service-account-name project-id
                                                     email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

;;;; Reitit routes

(comment
  (require '[reitit.core :as r])
  (-> (r/router ["http://localhost/{projects/*}/serviceAccounts" ::user-by-id]
                {:syntax :bracket})
      (r/match-by-path "http://localhost/projects/prj-123/serviceAccounts")))

(defn routes
  []
  [["/projects/{project-id}/serviceAccounts"
    {:get
     {:summary "Lists every ServiceAccount that belongs to a specific project"
      :parameters {:path [:map [:project-id string?]]}
      :responses
      {200 {:body [:map [:accounts [:vector service-account/ServiceAccount]]]}}
      :handler list-service-accounts}
     :post {:summary "Creates a ServiceAccount"
            :parameters {:path [:map [:project-id string?]]
                         :body service-account/CreateBody}
            :responses {201 {:body service-account/ServiceAccount}}
            :handler create-service-account}}]
   ["/projects/{project-id}/serviceAccounts/{email-or-unique-id}"
    {:parameters {:path {:project-id string? :email-or-unique-id string?}}
     :get {:summary "Gets a ServiceAccount"
           :responses {200 {:body service-account/ServiceAccount}}
           :handler get-service-account}
     :patch {:summary "Patches a ServiceAccount"
             :parameters {:body service-account/PatchBody}
             :responses {200 {:body service-account/ServiceAccount}}
             :handler patch-service-account}
     :delete {:summary "Deletes a ServiceAccount"
              :responses {204 {}}
              :handler delete-service-account}}]
   ["/projects/{project-id}/serviceAccounts/{email-or-unique-id}:undelete"
    {:parameters {:path {:project-id string? :email-or-unique-id string?}}
     :post {:summary "Undeletes a ServiceAccount that was deleted"
            :responses {204 {}}
            :handler undelete-service-account}}]
   ["/projects/{project-id}/serviceAccounts/{email-or-unique-id}:enable"
    {:parameters {:path {:project-id string? :email-or-unique-id string?}}
     :post {:summary "Enables a ServiceAccount that was disabled"
            :responses {204 {}}
            :handler enable-service-account}}]
   ["/projects/{project-id}/serviceAccounts/{email-or-unique-id}:disable"
    {:parameters {:path {:project-id string? :email-or-unique-id string?}}
     :post {:summary "Disables a ServiceAccount immediately"
            :responses {204 {}}
            :handler disable-service-account}}]])
