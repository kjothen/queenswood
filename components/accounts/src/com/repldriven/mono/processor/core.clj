(ns com.repldriven.mono.processor.core
  (:require
    [com.repldriven.mono.processor.commands.account-lifecycle :as
     account-lifecycle]
    [com.repldriven.mono.processor.specs.core :as specs]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.spec.interface :as spec]
    [com.repldriven.mono.json.interface :as json]))

(defn process
  "Process an account command and return result or anomaly.

  Config structure:
  {:datasource ...}

  Command structure (Avro schema):
  {\"id\" \"cmd-123\"
   \"command\" \"command-name\"
   \"data\" \"json-string\"
   \"correlation_id\" \"corr-456\"
   \"causation_id\" \"cause-789\"
   \"traceparent\" \"00-trace-span-01\"
   \"tracestate\" \"vendor=value\"}

  Returns success response or anomaly."
  [config {:strs [command data]}]
  (if-not command
    ;; No data field - check if it's an unknown command
    (error/fail :accounts/process-command
                {:message "Unknown command" :command command})
    ;; Parse JSON data
    (error/let-nom [data (json/read-str data)
                    schema (get specs/specs command)]
      (if (and schema (not (spec/validate schema data)))
        (error/fail :accounts/process-command
                    {:message "Invalid command data"
                     :command command
                     :reason (spec/humanize (spec/explain schema data))})
        (case command
          "open-account" (account-lifecycle/open config data)
          "close-account" (account-lifecycle/close config data)
          "reopen-account" (account-lifecycle/reopen config data)
          "suspend-account" (account-lifecycle/suspend config data)
          "unsuspend-account" (account-lifecycle/unsuspend config data)
          "archive-account" (account-lifecycle/archive config data)
          ;; Unknown command
          (error/fail :accounts/process-command
                      {:message "Unknown command" :command command}))))))
