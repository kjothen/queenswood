(ns com.repldriven.mono.iam.service-account
  (:refer-clojure :exclude [delete get list name])
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str]))

(def ^:private kebab {:builder-fn db/as-unqualified-kebab-maps})
(def ^:private no-keys {:return-keys false})

(defn email
  [account-id project-id]
  (str account-id "@" project-id ".iam.serviceaccount"))

(defn name [name email] (str name "/serviceAccounts/" email))

(defn project-id [project-name] (second (str/split project-name #"/")))

(defn ->ServiceAccount
  [record]
  (when record
    (-> record
        (dissoc :created-at :updated-at :deleted-at)
        (update :unique-id (fn [unique-id] (format "%021.0f" unique-id))))))

(defn create
  [db project-name account-id display-name description]
  (let
    [project-id (project-id project-name)
     email (email account-id project-id)
     result
     (db/execute-one!
      db
      ["INSERT INTO service_account
             (name, project_id, email, display_name, description, disabled)
           VALUES (?, ?, ?, ?, ?, FALSE)
           RETURNING unique_id, name, project_id, email,
                     display_name, description, disabled"
       (name project-name email)
       project-id
       email
       display-name
       description]
      kebab)]
    (if (error/anomaly? result) result (->ServiceAccount result))))

(defn delete
  [db name]
  (let
    [result
     (db/execute-one!
      db
      ["UPDATE service_account
           SET deleted_at = timezone('utc', now())
           WHERE name = ? AND deleted_at IS NULL"
       name]
      no-keys)]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account-not-found
                            {:message "Service account not found"
                             :name name}))))

(defn undelete
  [db name]
  (let
    [result
     (db/execute-one!
      db
      ["UPDATE service_account
           SET deleted_at = NULL, updated_at = timezone('utc', now())
           WHERE name = ? AND deleted_at IS NOT NULL"
       name]
      no-keys)]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account-not-found
                            {:message "Service account not found"
                             :name name}))))

(defn disable
  [db name]
  (let
    [result
     (db/execute-one!
      db
      ["UPDATE service_account
           SET disabled = TRUE, updated_at = timezone('utc', now())
           WHERE name = ? AND deleted_at IS NULL"
       name]
      no-keys)]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account-not-found
                            {:message "Service account not found"
                             :name name}))))

(defn enable
  [db name]
  (let
    [result
     (db/execute-one!
      db
      ["UPDATE service_account
           SET disabled = FALSE, updated_at = timezone('utc', now())
           WHERE name = ? AND deleted_at IS NULL"
       name]
      no-keys)]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account-not-found
                            {:message "Service account not found"
                             :name name}))))

(defn list
  [db project-name]
  (let
    [result
     (db/execute!
      db
      ["SELECT unique_id, name, project_id, email,
                  display_name, description, disabled
           FROM service_account
           WHERE name LIKE ? AND deleted_at IS NULL"
       (str project-name "/serviceAccounts/%")]
      kebab)]
    (if (error/anomaly? result) result (mapv ->ServiceAccount result))))

(defn get
  [db name]
  (let
    [result
     (db/execute-one!
      db
      ["SELECT unique_id, name, project_id, email,
                  display_name, description, disabled
           FROM service_account
           WHERE name = ? AND deleted_at IS NULL"
       name]
      kebab)]
    (if (error/anomaly? result) result (->ServiceAccount result))))

(defn patch
  [db name display-name description]
  (let
    [result
     (db/execute-one!
      db
      ["UPDATE service_account
           SET display_name = ?, description = ?,
               updated_at = timezone('utc', now())
           WHERE name = ? AND deleted_at IS NULL"
       display-name
       description
       name]
      no-keys)]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account-not-found
                            {:message "Service account not found"
                             :name name}))))
