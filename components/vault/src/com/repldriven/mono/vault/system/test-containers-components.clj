(ns com.repldriven.mono.vault.system.testcontainers-components
  (:require [clj-test-containers.core :as tc]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.testcontainers.containers ContainerLaunchException)
           (org.testcontainers.vault VaultContainer)
           (org.testcontainers.utility DockerImageName)))

(def default-exposed-port 8200)
(def default-docker-image-name "vault:latest")

(def container
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [docker-image-name exposed-port
                       vault-token secret-in-vault]} config]
           (try
             (let [container (-> (DockerImageName/parse docker-image-name)
                                 (.asCompatibleSubstituteFor "vault")
                                 (VaultContainer.))]
               (when vault-token
                 (.withVaultToken container vault-token))
               (when secret-in-vault
                 (.withSecretInVault container
                                     (first secret-in-vault)
                                     (second secret-in-vault)
                                     (into-array Stri
                                                 (nthrest secret-in-vault 2))))

               (some-> (tc/init {:container container
                                 :exposed-ports [exposed-port]})
                       (tc/start!)))
             (catch ContainerLaunchException e
               (log/error "Failed to start vault container, %s" e))))))
   :system/stop  (fn [{:system/keys [instance]}]
                   (tc/stop! instance))
   :system/config  {:docker-image-name default-docker-image-name
                    :exposed-port      default-exposed-port}})

(comment
  (require '[vault.core :as vault] 'vault.client.http)
  (require '[vault.secrets.kvv2 :as vault-kvv2])
  (require '[com.repldriven.mono.testcontainers-system.components :as components])

  (def start-fn (:system/start container))
  (def config (:system/config container))
  (def stop-fn (:system/stop container))

  (def con (start-fn {:system/config
                      (assoc config
                             :vault-token "my-root-token"
                             :secret-in-vault ["secret/testing"
                                               "top_secret=password1"
                                               "db_password=dbpassword1"])}))

  (def uri-start-fn (:system/start components/connection-uri))
  (def uri-config (assoc (:system/config components/connection-uri)
                         :container-mapped-ports (:mapped-ports con)
                         :exposed-port default-exposed-port))

  (def uri (uri-start-fn {:system/config uri-config}))
  (def client (vault/new-client uri))

  (vault/authenticate! client :token "my-root-token")
  (vault-kvv2/read-secret client "secret" "testing")

  (stop-fn {:system/instance con})
  )
