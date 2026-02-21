(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.handlers
  (:refer-clojure :exclude [get list name])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.iam.interface :as iam]
    [com.repldriven.mono.log.interface :as log]))

;;;; Helpers

(def unknown-service-account-error
  {:error {:code "404" :message "Unknown service account" :status "NOT_FOUND"}})

(defn- project-name [project-id] (str "projects/" project-id))

(defn- service-account-name
  [project-id email-or-unique-id]
  (str (project-name project-id) "/serviceAccounts/" email-or-unique-id))

(defn- not-found? [result] (error/anomaly? result))

;;;; Handlers

(defn create
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [body path]} parameters
        {:keys [project-id]} path
        {:strs [account-id service-account]} body
        {:strs [display-name description]} service-account
        result (iam/create-service-account datasource
                                           (project-name project-id)
                                           account-id
                                           display-name
                                           description)]
    (log/info "create" project-id body)
    {:status 201 :body result}))

(defn get
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id email-or-unique-id]} path
        result (iam/get-service-account
                datasource
                (service-account-name project-id email-or-unique-id))]
    (log/info "get" project-id email-or-unique-id)
    (cond (not-found? result) {:status 404 :body unknown-service-account-error}
          result {:status 200 :body result}
          :else {:status 404 :body unknown-service-account-error})))

(defn patch
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [body path]} parameters
        {:keys [project-id email-or-unique-id]} path
        {:strs [service-account]} body
        {:strs [display-name description]} service-account
        result (iam/patch-service-account
                datasource
                (service-account-name project-id email-or-unique-id)
                display-name
                description)]
    (log/info "patch" project-id email-or-unique-id body)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 204 :body {}})))

(defn delete
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id email-or-unique-id]} path
        result (iam/delete-service-account
                datasource
                (service-account-name project-id email-or-unique-id))]
    (log/info "delete" project-id email-or-unique-id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 204 :body {}})))

(defn undelete
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id email-or-unique-id]} path
        result (iam/undelete-service-account
                datasource
                (service-account-name project-id email-or-unique-id))]
    (log/info "undelete" project-id email-or-unique-id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 204 :body {}})))

(defn list
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id]} path
        result (iam/list-service-account datasource (project-name project-id))]
    (log/info "list" project-id)
    {:status 200 :body {:accounts result}}))

(defn enable
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id email-or-unique-id]} path
        result (iam/enable-service-account
                datasource
                (service-account-name project-id email-or-unique-id))]
    (log/info "enable" project-id email-or-unique-id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 204 :body {}})))

(defn disable
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id email-or-unique-id]} path
        result (iam/disable-service-account
                datasource
                (service-account-name project-id email-or-unique-id))]
    (log/info "disable" project-id email-or-unique-id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 204 :body {}})))
