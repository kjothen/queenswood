(ns com.repldriven.queenswood.changelog-relay.system
  (:require
    [com.repldriven.queenswood.changelog-relay.consumer :as consumer]
    [com.repldriven.queenswood.changelog-relay.envelope :as envelope]
    [com.repldriven.queenswood.changelog-relay.runner :as runner]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.system.interface :as system]))

(def ^:private runner-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (runner/start config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when-let [{:keys [stop]} instance] (stop)))
   :system/config {:record-db system/required-component
                   :consumer-id system/required-component
                   :store-name system/required-component
                   :handler system/required-component
                   :poll-ms nil
                   :keyspace-prefix nil}
   :system/instance-schema map?})

(def ^:private runners-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (update-vals config runner/start)))
   :system/stop (fn [{:system/keys [instance]}]
                  (doseq [[k {:keys [stop]}] instance]
                    (log/info "Stopping changelog relay:" (name k))
                    (stop)))
   :system/config system/required-component
   :system/instance-schema map?})

(def ^:private envelope-handler-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (envelope/->handler config)))
   :system/config {:bus system/required-component
                   :event-channel system/required-component
                   :store-name nil}
   :system/instance-schema fn?})

(def ^:private event-consumer-component
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance (consumer/start config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (when-let [{:keys [stop]} instance] (stop)))
   :system/config {:bus system/required-component
                   :event-channel system/required-component
                   :processor system/required-component}
   :system/instance-schema map?})

(system/defcomponents :changelog-relay
                      {:runner runner-component
                       :runners runners-component
                       :envelope-handler envelope-handler-component
                       :event-consumer event-consumer-component})
