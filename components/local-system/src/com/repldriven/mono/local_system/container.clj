(ns com.repldriven.mono.local-system.container
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]))

(def mapped-ports
  {:start (fn [{:keys [container]} instance _]
            (or instance
                (let [ports (:mapped-ports container)]
                  (log/info "Container mapped ports:" ports)
                  ports)))
   :stop  (fn [_ _ _])
   :conf  {:container system/required-component}})

(def mapped-exposed-port
  {:start (fn [{:keys [exposed-port container]} instance _]
            (or instance
              (let [port (get-in container [:mapped-ports exposed-port])]
                (log/info "Container mapped exposed port:" port)
                port)))
   :stop  (fn [_ _ _])
   :conf  {:container system/required-component
           :exposed-port system/required-component}})
