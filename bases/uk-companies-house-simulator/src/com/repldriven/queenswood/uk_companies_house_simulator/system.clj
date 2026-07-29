(ns com.repldriven.queenswood.uk-companies-house-simulator.system
  (:require
    [com.repldriven.queenswood.uk-companies-house-simulator.companies.examples
     :as examples]

    [com.repldriven.mono.system.interface :as system]))

(system/defcomponents
 :uk-companies-house-simulator
 {:state {:system/start (fn [{:system/keys [instance]}]
                          (or instance (atom {:companies examples/fixtures})))
          :system/instance-schema some?}})
