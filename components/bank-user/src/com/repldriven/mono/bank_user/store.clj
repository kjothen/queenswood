(ns com.repldriven.mono.bank-user.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private store-name "users")

(def transact fdb/transact)

(defn save
  "Insert or update a User (FDB save-record is upsert by primary key)."
  [txn user]
  (fdb/transact txn
                (fn [txn]
                  (fdb/save-record (fdb/open txn store-name)
                                   (schema/User->java user)))
                :user/save
                "Failed to save user"))

(defn get-user
  [txn user-id]
  (fdb/transact txn
                (fn [txn]
                  (if-let [record (fdb/load-record (fdb/open txn store-name)
                                                   user-id)]
                    (schema/pb->User record)
                    (error/reject :user/not-found
                                  {:message "User not found"
                                   :user-id user-id})))
                :user/get
                "Failed to load user"))

(defn find-by-keycloak-sub
  "Returns the User with the given Keycloak sub, or nil if absent.
  Uses the unique User_by_keycloak_sub index."
  [txn sub]
  (fdb/transact txn
                (fn [txn]
                  (first
                   (mapv schema/pb->User
                         (fdb/query-records
                          (fdb/open txn store-name)
                          "User"
                          "keycloak_sub"
                          sub
                          {:index "User_by_keycloak_sub"}))))
                :user/find-by-keycloak-sub
                "Failed to look up user by keycloak sub"))

(defn find-by-email
  "Returns Users with the given email (non-unique index)."
  [txn email]
  (fdb/transact txn
                (fn [txn]
                  (mapv schema/pb->User
                        (fdb/query-records
                         (fdb/open txn store-name)
                         "User"
                         "email"
                         email
                         {:index "User_by_email"})))
                :user/find-by-email
                "Failed to look up users by email"))
