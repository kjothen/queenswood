(ns com.repldriven.mono.processor.core
  (:require
   [com.repldriven.mono.processor.commands.account-lifecycle :as account-lifecycle]
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
      "open-account" (account-lifecycle/open config command-data)
      "close-account" (account-lifecycle/close config command-data)
      "reopen-account" (account-lifecycle/reopen config command-data)
      "suspend-account" (account-lifecycle/suspend config command-data)
      "unsuspend-account" (account-lifecycle/unsuspend config command-data)
      "archive-account" (account-lifecycle/archive config command-data)

      ;; Unknown command
      (error/fail :accounts/unknown-command
                  (str "Unknown command type: " command-type)))))
