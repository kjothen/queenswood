(ns com.repldriven.mono.mqtt.local-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [clojurewerkz.machine-head.client :as mh])
  (:import (org.testcontainers.containers ContainerLaunchException)
           (org.testcontainers.hivemq HiveMQContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 1883)
(def default-exposed-tls-port 8883)
(def default-docker-image-name "hivemq/hivemq-ce:latest")

(def container
  {:start (fn [{:keys [docker-image-name exposed-port]} instance _]
            (or instance
              (try
                (some-> (tc/init {:container (-> (DockerImageName/parse docker-image-name)
                                                 (.asCompatibleSubstituteFor "hivemq/hivemq-ce")
                                                 (HiveMQContainer.)),
                                  :exposed-ports [exposed-port]})
                  (tc/start!))
                (catch ContainerLaunchException e
                  (log/error "Failed to start mqtt container, %s" e))))),
   :stop (fn [_ instance _] (tc/stop! instance)),
   :conf {:docker-image-name default-docker-image-name,
          :exposed-port default-exposed-port}})

(def container-connection-uri
  {:start (fn [{:keys [container-mapped-ports exposed-port]} instance _]
            (or instance
                (let [connection-uri-str (str "tcp://localhost:" (get container-mapped-ports exposed-port))]
                  (log/info "Mapped mqtt container-connection-uri:" connection-uri-str)
                  connection-uri-str))),
   :stop (fn [_ _ _]),
   :conf {:container-mapped-ports system/required-component
          :exposed-port default-exposed-port}})