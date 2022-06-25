(ns com.repldriven.mono.postgres.local-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException PostgreSQLContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 5432)
(def default-docker-image-name "postgres:latest")

(def container
  {:start (fn [{:keys [docker-image-name exposed-port]} instance _]
            (or instance
              (try
                (-> (tc/init {:container     (PostgreSQLContainer. (DockerImageName/parse docker-image-name))
                              :exposed-ports [exposed-port]})
                  (tc/start!))
                (catch ContainerLaunchException e
                  (log/error "Failed to start postgres container, %s" e)))))
   :stop  (fn [_ instance _]
            (tc/stop! instance))
   :conf  {:docker-image-name default-docker-image-name
           :exposed-port      default-exposed-port}})
