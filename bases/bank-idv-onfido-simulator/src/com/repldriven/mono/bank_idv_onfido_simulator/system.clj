(ns com.repldriven.mono.bank-idv-onfido-simulator.system
  (:require
    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents
 :idv-onfido-simulator
 {:state {:system/start (fn [{:system/keys [instance]}]
                          (or instance
                              (atom {:applicants {} :checks {} :webhooks []})))
          :system/instance-schema some?}})
