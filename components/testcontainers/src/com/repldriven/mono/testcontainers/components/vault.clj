(ns com.repldriven.mono.testcontainers.components.vault
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log])
  (:import (org.testcontainers.containers ContainerLaunchException)
           (org.testcontainers.vault VaultContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-ports [8200])
(def default-docker-image-name "hashicorp/vault:latest")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-ports vault-token
                       secret-in-vault]}
               config]
           (try (let [container (-> (DockerImageName/parse docker-image-name)
                                    (.asCompatibleSubstituteFor "vault")
                                    (VaultContainer.))]
                  (when vault-token (.withVaultToken container vault-token))
                  (when secret-in-vault
                    (.withSecretInVault
                     container
                     (first secret-in-vault)
                     (second secret-in-vault)
                     (into-array String (nthrest secret-in-vault 2))))
                  (some-> (tc/init {:container container
                                    :exposed-ports exposed-ports})
                          (tc/start!)))
                (catch ContainerLaunchException e
                  (log/error "Failed to start vault container, %s" e))))))
   :system/stop (fn [{:system/keys [instance]}] (tc/stop! instance))
   :system/config {:docker-image-name default-docker-image-name
                   :exposed-ports default-exposed-ports}})
