(ns com.repldriven.mono.bank-idv.system
  (:require
    [com.repldriven.mono.bank-idv.commands :as commands]
    [com.repldriven.mono.bank-idv.events :as events]
    [com.repldriven.mono.bank-idv.watcher :as watcher]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (commands/->IdvProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(def ^:private event-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (events/->IdvEventProcessor config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :schemas system/required-component}
   :system/instance-schema some?})

(def ^:private party-watcher-handler
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (watcher/party-changelog-handler config)))
   :system/config {:record-store system/required-component}
   :system/instance-schema fn?})

(def ^:private idv-watcher-handler
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (watcher/idv-changelog-handler config)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :bus nil
                   :schemas nil
                   :command-channel nil}
   :system/instance-schema fn?})

(system/defcomponents :idv
                      {:processor processor
                       :event-processor event-processor
                       :party-watcher-handler party-watcher-handler
                       :idv-watcher-handler idv-watcher-handler})
