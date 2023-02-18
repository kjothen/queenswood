(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.routes
  (:require [com.repldriven.mono.log.interface :as log]
            [malli.generator :as mg]))

;;;; Malli schemas

(def ServiceAccount
  [:map [:name string?] [:project-id string?] [:unique-id string?]
   [:email string?] [:display-name string?] [:description string?]
   [:disabled boolean?]])

(def CreateRequest
  [:map [:account-id string?]
   [:service-account [:map [:display-name string?] [:description string?]]]])

(def PatchRequest
  [:map [:service-account [:map [:display-name string?] [:description string?]]]
   [:update-mask string?]])

;;;; Ring handlers

(defn create-service-account
  [{{{:keys [project-id]} :path {:keys [account-id service-account]} :body}
    :parameters}]
  (log/info "create-service-account" project-id account-id service-account)
  {:status 201 :body (mg/generate ServiceAccount)})

(defn get-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "get-service-account" project-id account-id)
  {:status 200 :body (mg/generate ServiceAccount)})

(defn patch-service-account
  [{{{:keys [project-id account-id]} :path
     {:keys [service-account update-mask]} :body}
    :parameters}]
  (log/info "patch-service-account"
            project-id
            account-id
            service-account
            update-mask)
  {:status 200 :body (mg/generate ServiceAccount)})

(defn delete-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "delete-service-account" project-id account-id)
  {:status 204 :body nil})

(defn list-service-accounts
  [{{{:keys [project-id]} :path} :parameters}]
  (log/info "list-service-accounts" project-id)
  {:status 200 :body {:accounts (vector (mg/generate ServiceAccount))}})

(defn enable-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "enable-service-account" project-id account-id)
  {:status 204 :body nil})

(defn disable-service-account
  [{{{:keys [project-id account-id]} :path} :parameters}]
  (log/info "disable-service-account" project-id account-id)
  {:status 204 :body nil})

;;;; Reitit routes

(defn routes
  []
  [["/service-accounts"
    {:get {:summary
           "Lists every ServiceAccount that belongs to a specific project"
           :parameters {:path [:map [:project-id int?]]}
           :responses {200 {:body [:map [:accounts [:vector ServiceAccount]]]}}
           :handler list-service-accounts}
     :post {:summary "Creates a ServiceAccount"
            :parameters {:path [:map [:project-id int?]] :body CreateRequest}
            :responses {201 {:body ServiceAccount}}
            :handler create-service-account}}]
   ["/service-accounts/{account-id}"
    {:parameters {:path {:project-id int? :account-id string?}}
     :get {:summary "Gets a ServiceAccount"
           :responses {200 {:body ServiceAccount}}
           :handler get-service-account}
     :patch {:summary "Patches a ServiceAccount"
             :parameters {:body PatchRequest}
             :responses {200 {:body ServiceAccount}}
             :handler patch-service-account}
     :delete {:summary "Deletes a ServiceAccount"
              :responses {204 {}}
              :handler delete-service-account}}]
   ["/service-accounts/{account-id}:enable"
    {:parameters {:path {:project-id int? :account-id string?}}
     :post {:summary "Enables a ServiceAccount that was disabled"
            :responses {204 {}}
            :handler enable-service-account}}]
   ["/service-accounts/{account-id}:disable"
    {:parameters {:path {:project-id int? :account-id string?}}
     :post {:summary "Disables a ServiceAccount immediately"
            :responses {204 {}}
            :handler disable-service-account}}]])
