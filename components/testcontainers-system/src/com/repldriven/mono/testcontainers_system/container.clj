(ns com.repldriven.mono.testcontainers-system.container
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]))

(def mapped-ports
  {:system/start (fn [{:keys [config instance]}]
                   (or instance
                       (let [{:keys [container]} config
                             ports (:mapped-ports container)]
                         (log/info "Container mapped ports:" ports)
                         ports)))
   :system/config  {:container system/required-component}})

(def mapped-exposed-port
  {:system/start (fn [{:keys [config instance]}]
                   (or instance
                       (let [{:keys [exposed-port container]} config
                             port (get-in container [:mapped-ports exposed-port])]
                         (log/info "Container mapped exposed port:" port)
                         port)))
   :system/config  {:container system/required-component
                    :exposed-port system/required-component}})
