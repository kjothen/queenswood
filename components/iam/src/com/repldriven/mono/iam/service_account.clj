(ns com.repldriven.mono.iam.service-account
  (:refer-clojure :exclude [delete get list name])
  (:require
    [com.repldriven.mono.db.interface :as db]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.sql.interface :as sql]

    [clojure.string :as str]))

(def ^:private select-cols
  [[[:lpad [:cast :unique_id :text] [:inline 21] [:inline "0"]] :unique_id]
   :name :project_id :email :display_name :description :disabled])

(defn email
  [account-id project-id]
  (str account-id "@" project-id ".iam.repldriven.com"))

(defn name [name email] (str name "/serviceAccounts/" email))

(defn project-id [project-name] (second (str/split project-name #"/")))

(defn create
  [db project-name account-id display-name description]
  (let [project-id (project-id project-name)
        email (email account-id project-id)]
    (db/execute-one! db
                     (sql/format
                      {:insert-into :service_account
                       :columns [:name :project_id :email :display_name
                                 :description :disabled]
                       :values [[(name project-name email) project-id email
                                 display-name description false]]
                       :returning select-cols})
                     {:builder-fn db/as-unqualified-kebab-maps})))

(defn delete
  [db name]
  (let [result (db/execute-one! db
                                (sql/format {:update :service_account
                                             :set {:deleted_at [:timezone "utc"
                                                                [:now]]}
                                             :where [:and [:= :name name]
                                                     [:= :deleted_at nil]]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
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
                  :where [:and [:= :name name] [:!= :deleted_at nil]]})
                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
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
                  :where [:and [:= :name name] [:= :deleted_at nil]]})
                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
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
                  :where [:and [:= :name name] [:= :deleted_at nil]]})
                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))

(defn list
  [db project-name]
  (db/execute! db
               (sql/format {:select select-cols
                            :from :service_account
                            :where [:and
                                    [:like :name
                                     (str project-name "/serviceAccounts/%")]
                                    [:= :deleted_at nil]]})
               {:builder-fn db/as-unqualified-kebab-maps}))

(defn get
  [db name]
  (db/execute-one! db
                   (sql/format {:select select-cols
                                :from :service_account
                                :where [:and [:= :name name]
                                        [:= :deleted_at nil]]})
                   {:builder-fn db/as-unqualified-kebab-maps}))

(defn patch
  [db name display-name description]
  (let [result (db/execute-one! db
                                (sql/format {:update :service_account
                                             :set {:display_name display-name
                                                   :description description
                                                   :updated_at [:timezone "utc"
                                                                [:now]]}
                                             :where [:and [:= :name name]
                                                     [:= :deleted_at nil]]})
                                {:return-keys false})]
    (cond (error/anomaly? result) result
          (pos? (db/update-count result)) {:name name}
          :else (error/fail :iam/service-account
                            {:message "Service account not found"
                             :name name}))))
