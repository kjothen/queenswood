(ns com.repldriven.queenswood.uk-companies-house-adapter.system
  (:require
    [com.repldriven.queenswood.uk-companies-house-adapter.commands :as commands]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private command-processor
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (commands/->UkCompaniesHouseCommandProcessor config)))
   :system/config {:schemas system/required-component
                   :record-db system/required-component
                   :record-store system/required-component
                   :companies-house-url system/required-component}
   :system/instance-schema some?})

(system/defcomponents :uk-companies-house-adapter
                      {:command-processor command-processor})
