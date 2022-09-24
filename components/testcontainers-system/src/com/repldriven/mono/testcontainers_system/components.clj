(ns com.repldriven.mono.testcontainers-system.components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException)))

(def container
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [docker-image-name exposed-ports]} config]
                         (try
                           (-> (tc/create {:image-name    docker-image-name
                                           :exposed-ports exposed-ports})
                             (tc/start!))
                           (catch ContainerLaunchException e
                             (log/error "Failed to start %s container, %s" docker-image-name e))))))
   :system/stop  (fn [{:system/keys [instance]}]
                   (tc/stop! instance))
   :system/config  {:docker-image-name system/required-component
                    :exposed-ports     system/required-component}})

(def mapped-ports
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container]} config
                             ports (:mapped-ports container)]
                         (log/info "Container mapped ports:" ports)
                         ports)))
   :system/config  {:container system/required-component}})

(def mapped-exposed-port
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [exposed-port container]} config
                             port (get-in container [:mapped-ports exposed-port])]
                         (log/info "Container mapped exposed port:" port)
                         port)))
   :system/config  {:container system/required-component
                    :exposed-port system/required-component}})

(def connection-uri
  {:system/start (fn [{:system/keys [config instance]}]
                   (or instance
                       (let [{:keys [container-mapped-ports
                                     exposed-port]} config
                             connection-uri-str (str "http://localhost:"
                                                     (get container-mapped-ports
                                                          exposed-port))]
                         (log/info "Mapped container connection-uri:"
                                   connection-uri-str)
                         connection-uri-str))),
   :system/config {:container-mapped-ports system/required-component
                   :exposed-port system/required-component}})
