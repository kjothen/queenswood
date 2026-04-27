(ns com.repldriven.mono.bank-organization.system
  (:require
    [com.repldriven.mono.bank-organization.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private organization
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [name type status tier policy currencies]} config]
           (core/new-organization {:record-db (:record-db config)
                                   :record-store (:record-store config)}
                                  name
                                  type
                                  (or status :organization-status-test)
                                  (:tier-id tier)
                                  currencies
                                  {:policies [policy]}))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :tier system/required-component
                   :policy system/required-component}
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
                       :internal-account-id internal-account-id})
