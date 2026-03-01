(ns com.repldriven.mono.iam-api.v1.projects.service-accounts.handlers
  (:refer-clojure :exclude [get list name])
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.iam.interface :as iam]
    [com.repldriven.mono.log.interface :as log]))

;;;; Helpers

(def unknown-service-account-error
  {:error {:code "404" :message "Unknown service account" :status "NOT_FOUND"}})

(def already-exists-error
  {:error {:code "409"
           :message "Service account already exists"
           :status "ALREADY_EXISTS"}})

(defn- project-name [project-id] (str "projects/" project-id))

(defn- service-account-name
  [project-id id]
  (str (project-name project-id) "/serviceAccounts/" id))

(defn- not-found? [result] (error/anomaly? result))

(defn- conflict? [result] (= :iam/service-account-create (error/kind result)))

;;;; Handlers

(defn create
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [body path]} parameters
        {:keys [project-id]} path
        {:keys [name display-name description]} body
        result (iam/create-service-account datasource
                                           (project-name project-id)
                                           name
                                           display-name
                                           description)]
    (log/info "create" project-id body)
    (cond
     (conflict? result)
     {:status 409 :body already-exists-error}

     (not-found? result)
     {:status 500 :body result}

     :else
     {:status 201 :body result})))

(defn get
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id id]} path
        result (iam/get-service-account datasource
                                        (service-account-name project-id id))]
    (log/info "get" project-id id)
    (cond
     (not-found? result)
     {:status 404 :body unknown-service-account-error}

     result
     {:status 200 :body result}

     :else
     {:status 404 :body unknown-service-account-error})))

(defn patch
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [body path]} parameters
        {:keys [project-id id]} path
        {:keys [display-name description]} body
        result (iam/patch-service-account datasource
                                          (service-account-name project-id id)
                                          display-name
                                          description)]
    (log/info "patch" project-id id body)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 200 :body result})))

(defn delete
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id id]} path
        result (iam/delete-service-account datasource
                                           (service-account-name project-id
                                                                 id))]
    (log/info "delete" project-id id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 200 :body result})))

(defn undelete
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id id]} path
        result (iam/undelete-service-account datasource
                                             (service-account-name project-id
                                                                   id))]
    (log/info "undelete" project-id id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 200 :body result})))

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
        {:keys [project-id id]} path
        result (iam/enable-service-account datasource
                                           (service-account-name project-id
                                                                 id))]
    (log/info "enable" project-id id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 200 :body result})))

(defn disable
  [request]
  (let [{:keys [datasource parameters]} request
        {:keys [path]} parameters
        {:keys [project-id id]} path
        result (iam/disable-service-account datasource
                                            (service-account-name project-id
                                                                  id))]
    (log/info "disable" project-id id)
    (if (not-found? result)
      {:status 404 :body unknown-service-account-error}
      {:status 200 :body result})))
