(ns com.repldriven.mono.bank-clearbank-relay.system
  (:require
    [com.repldriven.mono.bank-clearbank-relay.relay :as relay]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private relay-handler
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (relay/->handler config)))
   :system/config {:bus system/required-component
                   :event-channel system/required-component}
   :system/instance-schema fn?})

(system/defcomponents :clearbank-relay {:relay-handler relay-handler})
