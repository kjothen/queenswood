(ns com.repldriven.mono.processor.core
  (:require
   [com.repldriven.mono.processor.commands.account :as account]
   [com.repldriven.mono.error.interface :as error]))

(defn process
  "Process an account command and return result or anomaly.

  Config structure:
  {:datasource ...}

  Command structure:
  {:type \"command-type\"
   :id \"command-id\"
   :data {...}}

  Returns success response or anomaly."
  [config command]
  (let [command-type (:type command)
        command-data (:data command)]
    (case command-type
      "open-account" (account/open config command-data)
      "close-account" (account/close config command-data)

      ;; Unknown command
      (error/fail :accounts/unknown-command
                  (str "Unknown command type: " command-type)))))
