(ns com.repldriven.mono.command-processor.processor
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]))

(defn run
  "Start command processing on the given system.

  Extracts the message-bus and processor from the system and
  starts the command processing loop.

  Returns: {:stop (fn [])} — call stop to stop processing"
  [sys]
  (let [bus (system/instance sys [:message-bus :bus])
        processor-instance (system/instance sys [:command :processor])]
    (command/process bus #(processor/process processor-instance %))))
