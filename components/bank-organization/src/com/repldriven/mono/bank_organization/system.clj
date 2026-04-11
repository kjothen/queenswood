(ns com.repldriven.mono.bank-organization.system
  (:require
    [com.repldriven.mono.bank-organization.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private organization
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [name type tier-type currencies]} config]
                         (core/new-organization {:record-db (:record-db config)
                                                 :record-store (:record-store
                                                                config)}
                                                name
                                                type
                                                tier-type
                                                currencies))))
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
                       :internal-account-id internal-account-id})
