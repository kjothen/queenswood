(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.handlers
  (:refer-clojure :exclude [get list name])
  (:require
    [com.repldriven.mono.iam.interface :as iam]
    [com.repldriven.mono.log.interface :as log]))

;;;; Helpers

(def unknown-service-account-error
  {:error {:code "404" :message "Unknown service account" :status "NOT_FOUND"}})

(defn- project-name [project-id] (str "projects/" project-id))

(defn- service-account-name
  [project-id email-or-unique-id]
  (str (project-name project-id) "/serviceAccounts/" email-or-unique-id))

;;;; Handlers

(defn create
  [{:keys [datasource] {:keys [body] {:keys [project-id]} :path} :parameters}]
  (log/info "create" project-id body)
  (let [{account-id "account-id"
         {display-name "display-name" description "description"}
         "service-account"}
        body
        result (iam/create-service-account datasource
                                           (project-name project-id)
                                           account-id
                                           display-name
                                           description)]
    {:status 201 :body result}))

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
  (let [{{display-name "display-name" description "description"}
         "service-account"}
        body]
    (if (iam/patch-service-account datasource
                                   (service-account-name project-id
                                                         email-or-unique-id)
                                   display-name
                                   description)
      {:status 204 :body {}}
      {:status 404 :body unknown-service-account-error})))

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
