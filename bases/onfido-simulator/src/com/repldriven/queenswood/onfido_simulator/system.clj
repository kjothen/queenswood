(ns com.repldriven.queenswood.onfido-simulator.system
  (:require
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents
 :onfido-simulator
 {:state {:system/start (fn [{:system/keys [instance]}]
                          (or instance
                              (atom {:applicants {} :checks {} :webhooks []})))
          :system/instance-schema some?}})
