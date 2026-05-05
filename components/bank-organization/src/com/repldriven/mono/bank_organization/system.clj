(ns com.repldriven.mono.bank-organization.system
  (:require
    [com.repldriven.mono.bank-organization.core :as core]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.system.interface :as system]))

;; Idempotent in-process seeder. On first start it provisions the
;; organization (with API key, party, product, accounts); subsequent
;; starts (or any start where an organization of `type` already
;; exists) re-enrich and return the existing record. Used by
;; bank-monolith and the bank-bootstrap-service Job.
(def ^:private organization
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [record-db record-store name type status tier policy
                       currencies]}
               config
               txn {:record-db record-db :record-store record-store}]
           (let-nom> [existing (core/get-organizations-by-type txn type)]
             (if-let [org (first existing)]
               (core/get-organization txn org)
               (core/new-organization txn
                                      name
                                      type
                                      (or status :organization-status-test)
                                      tier
                                      currencies
                                      {:policies [policy]}))))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :policy system/required-component}
   :system/instance-schema map?})

;; Read-only discovery of an existing organization from FDB. Used
;; by services that run after bank-bootstrap has seeded the
;; internal organization. Fails on start if no organization of
;; `type` exists.
(def ^:private organization-from-fdb
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [record-db record-store type]} config
               txn {:record-db record-db :record-store record-store}]
           (let-nom> [orgs (core/get-organizations-by-type txn type)]
             (if-let [org (first orgs)]
               (core/get-organization txn org)
               (error/fail :organizations/organization-not-found
                           {:message
                            "Organization of given type not found in FDB"
                            :type type}))))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(def ^:private internal-account-id
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (get-in (:organization config)
                               [:organization :accounts 0 :account-id])))
   :system/config {:organization system/required-component}
   :system/instance-schema string?})

(system/defcomponents :organizations
                      {:organization organization
                       :organization-from-fdb organization-from-fdb
                       :internal-account-id internal-account-id})
