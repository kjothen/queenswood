(ns com.repldriven.mono.processor.core
  (:require
   [com.repldriven.mono.processor.commands.account-lifecycle :as account-lifecycle]
   [com.repldriven.mono.processor.specs.core :as specs]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.spec-malli.interface :as spec]
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
  [config command]
  (let [command-type (get command "command")
        command-data-str (get command "data")
        correlation-id (get command "correlation_id")]
    (if-not command-data-str
        ;; No data field - check if it's an unknown command
        (error/fail :accounts/process-command
                    {:message "Unknown command type"
                     :command-type command-type
                     :correlation-id correlation-id})
        ;; Parse JSON data
        (error/let-nom
          [command-data (json/read-str command-data-str)
           schema (get specs/specs command-type)]
          (if (and schema (not (spec/validate schema command-data)))
            (error/fail :accounts/process-command
                        {:message "Invalid command data"
                         :command-type command-type
                         :correlation-id correlation-id
                         :validation-errors (spec/humanize (spec/explain schema command-data))})
            (case command-type
              "open-account" (account-lifecycle/open config command-data)
              "close-account" (account-lifecycle/close config command-data)
              "reopen-account" (account-lifecycle/reopen config command-data)
              "suspend-account" (account-lifecycle/suspend config command-data)
              "unsuspend-account" (account-lifecycle/unsuspend config command-data)
              "archive-account" (account-lifecycle/archive config command-data)

              ;; Unknown command
              (error/fail :accounts/process-command
                          {:message "Unknown command type"
                           :command-type command-type
                           :correlation-id correlation-id})))))))
