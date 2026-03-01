(ns com.repldriven.mono.command.system
  (:require
    [com.repldriven.mono.command.dispatcher :as dispatcher]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents
 :command
 {:dispatcher {:system/start (fn [{:system/keys [config instance]}]
                               (or instance (dispatcher/start (:bus config))))
               :system/stop (fn [{:system/keys [instance]}]
                              (when instance (dispatcher/stop instance)))
               :system/config {:bus system/required-component}}})
