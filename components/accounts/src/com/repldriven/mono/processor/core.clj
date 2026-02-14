(ns com.repldriven.mono.processor.core
  (:require
   [com.repldriven.mono.error.interface :as error]))

(defn process
  "Process an account command and return result or anomaly.

  Command structure:
  {:type \"command-type\"
   :id \"command-id\"
   :data {...}}

  Returns success response or anomaly."
  [command]
  (let [command-type (:type command)]
    (case command-type
      ;; Stub implementations - return success for now
      "open-account" {:status :ok :command-id (:id command)}
      "close-account" {:status :ok :command-id (:id command)}

      ;; Unknown command
      (error/fail :accounts/unknown-command
                  (str "Unknown command type: " command-type)))))
