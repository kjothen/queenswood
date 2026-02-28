(ns com.repldriven.mono.accounts.system.components
  (:require
    [com.repldriven.mono.accounts.core :as core]

    [com.repldriven.mono.system.interface :as system]))

(def processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (core/->AccountProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})
