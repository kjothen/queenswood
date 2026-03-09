(ns com.repldriven.mono.accounts.system
  (:require
    [com.repldriven.mono.accounts.core :as core]
    [com.repldriven.mono.accounts.watcher :as watcher]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->AccountProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(def ^:private watcher-handler
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance (watcher/account-changelog-handler (:record-store config))))
   :system/config {:record-store system/required-component}
   :system/instance-schema fn?})

(system/defcomponents :accounts
                      {:processor processor :watcher-handler watcher-handler})
