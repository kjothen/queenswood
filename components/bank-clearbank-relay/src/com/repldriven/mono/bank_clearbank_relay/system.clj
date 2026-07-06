(ns com.repldriven.mono.bank-clearbank-relay.system
  (:require
    [com.repldriven.mono.bank-clearbank-relay.outbound :as outbound]
    [com.repldriven.mono.bank-clearbank-relay.relay :as relay]

    [com.repldriven.mono.system.interface :as system]))

(def ^:private relay-handler
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (relay/->handler config)))
   :system/config {:bus system/required-component
                   :event-channel system/required-component}
   :system/instance-schema fn?})

(def ^:private outbound-runner
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (outbound/start-runner config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when-let [{:keys [stop]} instance] (stop)))
   :system/config {:record-db system/required-component
                   :record-store system/required-component
                   :clearbank-url system/required-component
                   :max-attempts nil
                   :poll-ms nil}
   :system/instance-schema map?})

(system/defcomponents :clearbank-relay
                      {:relay-handler relay-handler
                       :outbound-runner outbound-runner})
