(ns com.repldriven.mono.pulsar-mqtt-processor.processor
  (:require
    com.repldriven.mono.mqtt.interface

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.pulsar.interface :as pulsar]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]

    [clojure.walk :as walk]))

(defn run
  "Start command processing on the given system.

  Extracts the Pulsar consumer, MQTT client, schema, and processor from the system
  and starts the command processing loop.

  Returns: {:c channel :stop channel}
  - Send to :stop channel to stop processing"
  [sys]
  (let [consumer (system/instance sys [:pulsar :consumers :command])
        mqtt-client (system/instance sys [:mqtt :client])
        schemas (system/instance sys [:pulsar :schemas])
        schema (pulsar/schema->avro (get-in schemas [:command :schema]))
        processor-instance (system/instance sys [:processor])
        process-fn
        (fn [data]
          (let [parent-ctx (telemetry/extract-parent-context data)
                str-data (walk/stringify-keys data)]
            (telemetry/with-span-parent
             "process-command"
             parent-ctx
             (select-keys data [:id :command :correlation_id :causation_id])
             #(processor/process processor-instance str-data))))]
    (command/process consumer mqtt-client schema process-fn)))
