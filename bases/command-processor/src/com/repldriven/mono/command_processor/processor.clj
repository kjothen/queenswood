(ns com.repldriven.mono.command-processor.processor
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]))

(defn- start-processor
  [bus sys group]
  (let [processor-instance (system/instance sys [group :processor])
        command-ch (keyword (str (name group) "-command"))
        response-ch (keyword
                     (str (name group) "-command-response"))]
    (command/process bus
                     #(processor/process processor-instance %)
                     {:command-channel command-ch
                      :command-response-channel response-ch})))

(defn run
  "Start command processing on the given system.

  Starts a processing loop for each domain group, each on
  its own command/response channels.

  Args:
  - sys: started system
  - groups: seq of domain group keywords
    (e.g. [:accounts :parties])

  Returns: {:stop (fn [])} — call stop to stop all"
  [sys groups]
  (let [bus (system/instance sys [:message-bus :bus])
        processors (mapv #(start-processor bus sys %)
                         groups)]
    {:stop (fn []
             (doseq [{:keys [stop]} processors]
               (stop)))}))
