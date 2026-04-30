(ns com.repldriven.mono.bank-policy.system
  (:require
    [com.repldriven.mono.bank-policy.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private policy
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [record-db record-store policy]} config]
                         (core/new-policy {:record-db record-db
                                           :record-store record-store}
                                          policy))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(def ^:private policy-binding
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [record-db record-store binding]} config]
                         (core/new-binding {:record-db record-db
                                            :record-store record-store}
                                           binding))))
   :system/config {:record-db system/required-component
                   :record-store system/required-component}
   :system/instance-schema map?})

(system/defcomponents :policies {:policy policy :binding policy-binding})
