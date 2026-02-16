(ns com.repldriven.mono.pulsar-mqtt-processor.main
  (:require
   com.repldriven.mono.mqtt.interface

   [com.repldriven.mono.cli.interface :as cli]
   [com.repldriven.mono.command.interface :as command]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.processor.interface :as processor]
   [com.repldriven.mono.pulsar.interface :as pulsar]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.telemetry.interface :as telemetry]

   [clojure.core.async :as async])
  (:gen-class))

(defn start
  [config-file profile]
  (error/nom-> (env/config config-file profile)
               system/defs
               system/start))

(defn run
  "Start command processing on the given system.

  Extracts the Pulsar consumer, MQTT client, schema, and processor from the system
  and starts the command processing loop.

  Returns: {:c channel :stop channel}
  - Send to :stop channel to stop processing"
  [sys]
  (let [consumer (system/instance sys [:pulsar :consumers :c1])
        mqtt-client (system/instance sys [:mqtt :client])
        schemas (system/instance sys [:pulsar :schemas])
        schema (pulsar/schema->avro (get-in schemas [:command :schema]))
        processor-instance (system/instance sys [:processor])
        process-fn (fn [data]
                     (let [parent-ctx (telemetry/extract-parent-context data)]
                       (telemetry/with-span-parent
                         "process-command"
                         parent-ctx
                         (select-keys data ["id" "command" "correlation_id" "causation_id"])
                         #(processor/process processor-instance data))))]
    (command/process consumer mqtt-client schema process-fn)))

(defn -main
  [& args]
  (log/info args)
  (let [{:keys [options exit-message ok?]} (cli/validate-args
                                            "pulsar-mqtt-processor"
                                            args)]
    (if exit-message
      (cli/exit ok? exit-message)
      (let [{:keys [config-file profile]} options
            sys (start config-file (keyword profile))]
        (if (error/anomaly? sys)
          (cli/exit false
                    (str "Failed to start [" (error/kind sys)
                         "]: " (or (:message sys) "Unknown error")))
          (let [{:keys [stop]} (run sys)]
            (log/info "System started successfully")
            (async/<!! stop)))))))
