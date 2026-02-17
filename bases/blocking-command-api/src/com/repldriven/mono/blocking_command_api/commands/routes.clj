(ns com.repldriven.mono.blocking-command-api.commands.routes
  (:require
   [com.repldriven.mono.blocking-command-api.commands.handlers :as handlers]
   [com.repldriven.mono.command.interface :as command]
   [com.repldriven.mono.telemetry.interface :as telemetry]))

(defn routes
  [ctx]
  (let [specs @command/specs]
    ["/api" {:interceptors (concat telemetry/trace-span
                                   (:interceptors ctx))}
     ["/command"
      {:interceptors [telemetry/require-idempotency-key
                      telemetry/extract-correlation-id]
       :post {:summary "Submit a command and receive its result"
              :parameters {:body (:command-request specs)}
              :responses {200 {:body (:command-response specs)}}
              :handler handlers/create}}]]))
