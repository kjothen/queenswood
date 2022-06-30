(ns com.repldriven.mono.postgres.local-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException PostgreSQLContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 5432)
(def default-docker-image-name "postgres:latest")

(def container
  {:system/start (fn [{:keys [config instance]}]
                   (or instance
                       (let [{:keys [docker-image-name exposed-port]} config]
                         (try
                           (-> (tc/init {:container     (PostgreSQLContainer. (DockerImageName/parse docker-image-name))
                                         :exposed-ports [exposed-port]})
                               (tc/start!))
                           (catch ContainerLaunchException e
                             (log/error "Failed to start postgres container, %s" e))))))
   :system/stop  (fn [{:keys [instance]}]
                   (tc/stop! instance))
   :system/config  {:docker-image-name default-docker-image-name
                    :exposed-port      default-exposed-port}})
