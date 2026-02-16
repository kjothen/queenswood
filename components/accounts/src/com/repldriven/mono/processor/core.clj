(ns com.repldriven.mono.processor.core
  (:require
   [com.repldriven.mono.processor.commands.account-lifecycle :as account-lifecycle]
   [com.repldriven.mono.processor.spec.account-lifecycle :as spec]
   [com.repldriven.mono.error.interface :as error]
   [malli.core :as m]
   [malli.error :as me]))

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
        command-data (:data command)
        schema (get spec/specs command-type)]
    (if (and schema (not (m/validate schema command-data)))
      (error/fail :accounts/invalid-command-data
                  (str "Invalid command data: " (me/humanize (m/explain schema command-data))))
      (case command-type
        "open-account" (account-lifecycle/open config command-data)
        "close-account" (account-lifecycle/close config command-data)
        "reopen-account" (account-lifecycle/reopen config command-data)
        "suspend-account" (account-lifecycle/suspend config command-data)
        "unsuspend-account" (account-lifecycle/unsuspend config command-data)
        "archive-account" (account-lifecycle/archive config command-data)

        ;; Unknown command
        (error/fail :accounts/unknown-command
                    (str "Unknown command type: " command-type))))))
