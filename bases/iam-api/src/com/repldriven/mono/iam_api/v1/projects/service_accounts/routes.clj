(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-service-account.interface :as
             service-account]
            [malli.generator :as mg]))

;;;; Ring handlers

(defn- project-name [project-id] (str "projects/" project-id))

(defn create-service-account
  [req]
  (let [{:keys [datasource]
         {:keys [body] {:keys [project-id]} :path} :parameters}
        req]
    (tap> project-id)
    (tap> body)
    (let [account
          (service-account/create datasource (project-name project-id) body)]
      {:status 200 :body account})))

(defn get-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "get-service-account" project-id account-id)
  {:status 200 :body (mg/generate service-account/ServiceAccount)})

(defn patch-service-account
  [{{{:keys [project-id account-id]} :path
     {:keys [service-account update-mask]} :body}
    :parameters}]
  (log/info "patch-service-account"
            project-id
            account-id
            service-account
            update-mask)
  {:status 200 :body (mg/generate service-account/ServiceAccount)})

(defn delete-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "delete-service-account" project-id account-id)
  {:status 204 :body nil})

(defn list-service-accounts
  [{{{:keys [project-id]} :path} :parameters}]
  (log/info "list-service-accounts" project-id)
  {:status 200
   :body {:accounts (vector (mg/generate service-account/ServiceAccount))}})

(defn enable-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "enable-service-account" project-id account-id)
  {:status 204 :body nil})

(defn disable-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "disable-service-account" project-id account-id)
  {:status 204 :body nil})

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
   ["/projects/{project-id}/serviceAccounts}/{account-id}"
    {:parameters {:path {:project-id string? :account-id string?}}
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
   ["/projects/{project-id}/serviceAccounts/{account-id}:enable"
    {:parameters {:path {:project-id string? :account-id string?}}
     :post {:summary "Enables a ServiceAccount that was disabled"
            :responses {204 {}}
            :handler enable-service-account}}]
   ["/projects/{project-id}/serviceAccounts/{account-id}:disable"
    {:parameters {:path {:project-id string? :account-id string?}}
     :post {:summary "Disables a ServiceAccount immediately"
            :responses {204 {}}
            :handler disable-service-account}}]])
