(ns com.repldriven.mono.bank-user.core
  (:require
    [com.repldriven.mono.bank-user.domain :as domain]
    [com.repldriven.mono.bank-user.store :as store]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn upsert-by-keycloak-sub
  "Idempotent upsert keyed by Keycloak sub. On first call (sub is
  new) creates a User; on subsequent calls applies the fresh OIDC
  claims to the existing record. Returns the User map."
  [txn {:keys [keycloak-sub] :as claims}]
  (store/transact
   txn
   (fn [txn]
     (let-nom> [existing (store/find-by-keycloak-sub txn keycloak-sub)]
       (let [user (if existing
                    (domain/apply-claims existing claims)
                    (domain/new-user claims))]
         (let-nom> [_ (store/save txn user)]
           user))))
   :user/upsert
   "Failed to upsert user"))

(defn find-by-keycloak-sub
  "Returns the User with the given Keycloak sub or nil if absent."
  [txn sub]
  (store/find-by-keycloak-sub txn sub))

(defn find-by-id
  [txn user-id]
  (store/get-user txn user-id))
