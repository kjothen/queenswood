(ns com.repldriven.mono.bank-tier.system
  (:require
    [com.repldriven.mono.bank-tier.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private tier
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [name policies limits]} config]
                         (core/new-tier {:record-db (:record-db config)
                                         :record-store (:record-store config)}
                                        name
                                        policies
                                        limits))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(system/defcomponents :tiers {:tier tier})
