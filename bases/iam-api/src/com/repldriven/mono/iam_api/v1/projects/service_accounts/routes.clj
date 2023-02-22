(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:refer-clojure :exclude [get list name])
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam.interface :as iam]))

;;;; Ring handlers

(def unknown-service-account-error
  {:error {:code "404" :message "Unknown service account" :status "NOT_FOUND"}})

(defn- project-name [project-id] (str "projects/" project-id))
(defn- service-account-name
  [project-id email-or-unique-id]
  (str (project-name project-id) "/serviceAccounts/" email-or-unique-id))

(defn create
  [{:keys [datasource] {:keys [body] {:keys [project-id]} :path} :parameters}]
  (log/info "create" project-id body)
  (let [result
        (iam/create-service-account datasource (project-name project-id) body)]
    {:status 200 :body result}))

(defn get
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "get" project-id email-or-unique-id)
  (let [result (iam/get-service-account
                datasource
                (service-account-name project-id email-or-unique-id))]
    (if result
      {:status 200 :body result}
      {:status 404 :body unknown-service-account-error})))

(defn patch
  [{:keys [datasource]
    {:keys [body] {:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "patch" project-id email-or-unique-id body)
  (if (iam/patch-service-account datasource
                                 (service-account-name project-id
                                                       email-or-unique-id)
                                 body)
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn delete
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "delete" project-id email-or-unique-id)
  (if (iam/delete-service-account datasource
                                  (service-account-name project-id
                                                        email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn undelete
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "undelete" project-id email-or-unique-id)
  (if (iam/undelete-service-account datasource
                                    (service-account-name project-id
                                                          email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn list
  [{:keys [datasource] {{:keys [project-id]} :path} :parameters}]
  (log/info "list" project-id)
  {:status 200
   :body {:accounts (iam/list-service-account datasource
                                              (project-name project-id))}})

(defn enable
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "enable" project-id email-or-unique-id)
  (if (iam/enable-service-account datasource
                                  (service-account-name project-id
                                                        email-or-unique-id))
    {:status 204 :body {}}
    {:status 404 :body unknown-service-account-error}))

(defn disable
  [{:keys [datasource]
    {{:keys [project-id email-or-unique-id]} :path} :parameters}]
  (log/info "disable" project-id email-or-unique-id)
  (if (iam/disable-service-account datasource
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
  [["/serviceAccounts"
    {:get
     {:summary "Lists every ServiceAccount that belongs to a specific project"
      :responses {200 {:body [:map [:accounts [:vector iam/ServiceAccount]]]}}
      :handler list}
     :post {:summary "Creates a ServiceAccount"
            :parameters {:body iam/ServiceAccountCreateBody}
            :responses {201 {:body iam/ServiceAccount}}
            :handler create}}]
   ["/serviceAccounts/{email-or-unique-id}"
    {:parameters {:path {:email-or-unique-id iam/EmailAddressOrUniqueId}}
     :get {:summary "Gets a ServiceAccount"
           :responses {200 {:body iam/ServiceAccount}}
           :handler get}
     :patch {:summary "Patches a ServiceAccount"
             :parameters {:body iam/ServiceAccountPatchBody}
             :responses {200 {:body iam/ServiceAccount}}
             :handler patch}
     :delete
     {:summary "Deletes a ServiceAccount" :responses {204 {}} :handler delete}}]
   ["/serviceAccounts/{email-or-unique-id}:undelete"
    {:parameters {:path {:email-or-unique-id iam/EmailAddressOrUniqueId}}
     :post {:summary "Undeletes a ServiceAccount that was deleted"
            :responses {204 {}}
            :handler undelete}}]
   ["/serviceAccounts/{email-or-unique-id}:enable"
    {:parameters {:path {:email-or-unique-id string?}}
     :post {:summary "Enables a ServiceAccount that was disabled"
            :responses {204 {}}
            :handler enable}}]
   ["/serviceAccounts/{email-or-unique-id}:disable"
    {:parameters {:path {:email-or-unique-id string?}}
     :post {:summary "Disables a ServiceAccount immediately"
            :responses {204 {}}
            :handler disable}}]])
