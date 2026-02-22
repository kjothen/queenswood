(ns com.repldriven.mono.iam.service-account
  (:refer-clojure :exclude [delete get list name])
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]

    [clojure.string :as str]))

(def ^:private select-cols
  [:unique_id :name :project_id :email :display_name :description :disabled])

(defn- format-result
  [{db-name :name :as result}]
  (-> result
      (dissoc :name)
      (assoc :id db-name :name (first (str/split (:email result) #"@")))
      (update :unique-id #(format "%021d" (biginteger %)))))

(defn- name-where
  "WHERE clause fragment for a service account name path. Matches by
  email-based name or, when the identifier is a 21-digit numeric
  string, by project_id + unique_id."
  [sa-name]
  (let [parts (str/split sa-name #"/")
        identifier (last parts)]
    (if (re-matches #"\d{21}" identifier)
      [:and [:= :project_id (nth parts 1)]
       [:= :unique_id (Long/parseLong identifier)]]
      [:= :name sa-name])))

(defn email
  [account-id project-id]
  (str account-id "@" project-id ".iam.repldriven.com"))

(defn name [name email] (str name "/serviceAccounts/" email))

(defn project-id [project-name] (second (str/split project-name #"/")))

(defn create
  [db project-name account-id display-name description]
  (let [project-id (project-id project-name)
        email (email account-id project-id)
        result (db/execute-one!
                db
                (sql/format {:insert-into :service_account
                             :columns [:name :project_id :email :display_name
                                       :description :disabled]
                             :values [[(name project-name email) project-id
                                       email display-name description false]]
                             :returning select-cols})
                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else result)))

(defn delete
  [db name]
  (let [result (db/execute-one! db
                                (sql/format {:update :service_account
                                             :set {:deleted_at [:timezone "utc"
                                                                [:now]]}
                                             :where [:and (name-where name)
                                                     [:= :deleted_at nil]]
                                             :returning select-cols})
                                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))

(defn undelete
  [db name]
  (let [result (db/execute-one!
                db
                (sql/format
                 {:update :service_account
                  :set {:deleted_at nil :updated_at [:timezone "utc" [:now]]}
                  :where [:and (name-where name) [:!= :deleted_at nil]]
                  :returning select-cols})
                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))

(defn disable
  [db name]
  (let [result (db/execute-one!
                db
                (sql/format
                 {:update :service_account
                  :set {:disabled true :updated_at [:timezone "utc" [:now]]}
                  :where [:and (name-where name) [:= :deleted_at nil]]
                  :returning select-cols})
                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))

(defn enable
  [db name]
  (let [result (db/execute-one!
                db
                (sql/format
                 {:update :service_account
                  :set {:disabled false :updated_at [:timezone "utc" [:now]]}
                  :where [:and (name-where name) [:= :deleted_at nil]]
                  :returning select-cols})
                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))

(defn list
  [db project-name]
  (let [result (db/execute! db
                            (sql/format {:select select-cols
                                         :from :service_account
                                         :where [:and
                                                 [:like :name
                                                  (str project-name
                                                       "/serviceAccounts/%")]
                                                 [:= :deleted_at nil]]})
                            {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          :else (mapv format-result result))))

(defn get
  [db name]
  (let [result (db/execute-one! db
                                (sql/format {:select select-cols
                                             :from :service_account
                                             :where [:and (name-where name)
                                                     [:= :deleted_at nil]]})
                                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else result)))

(defn patch
  [db name display-name description]
  (let [result (db/execute-one! db
                                (sql/format {:update :service_account
                                             :set {:display_name display-name
                                                   :description description
                                                   :updated_at [:timezone "utc"
                                                                [:now]]}
                                             :where [:and (name-where name)
                                                     [:= :deleted_at nil]]
                                             :returning select-cols})
                                {:builder-fn db/as-unqualified-kebab-maps})]
    (cond (error/anomaly? result) result
          result (format-result result)
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))
