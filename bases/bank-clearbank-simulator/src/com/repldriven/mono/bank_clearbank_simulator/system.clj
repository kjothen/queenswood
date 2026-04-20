(ns com.repldriven.mono.bank-clearbank-simulator.system
  (:require
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :clearbank-simulator
                      {:state {:system/start (fn [{:system/keys [instance]}]
                                               (or instance (atom {})))
                               :system/instance-schema some?}})
