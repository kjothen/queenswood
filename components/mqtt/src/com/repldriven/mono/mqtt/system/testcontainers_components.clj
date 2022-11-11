(ns com.repldriven.mono.mqtt.system.testcontainers-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException)
           (org.testcontainers.hivemq HiveMQContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 1883)
(def default-docker-image-name "hivemq/hivemq-ce:latest")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-port]} config]
           (try (some->
                 (tc/init {:container
                           (-> (DockerImageName/parse docker-image-name)
                               (.asCompatibleSubstituteFor "hivemq/hivemq-ce")
                               (HiveMQContainer.))
                           :exposed-ports [exposed-port]})
                 (tc/start!))
                (catch ContainerLaunchException e
                  (log/error "Failed to start mqtt container, %s" e))))))
   :system/stop (fn [{:system/keys [instance]}] (tc/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-port default-exposed-port}})

(def container-connection-uri
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or
      instance
      (let [{:keys [container-mapped-ports exposed-port]} config
            connection-uri-str (str "tcp://localhost:"
                                    (get container-mapped-ports exposed-port))]
        (log/info "Mapped mqtt container-connection-uri:" connection-uri-str)
        connection-uri-str)))
   :system/config {:container-mapped-ports system/required-component
                   :exposed-port default-exposed-port}})
