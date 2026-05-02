(ns com.repldriven.mono.bank-idv-onfido-webhook.examples
  "Worked examples for the Onfido-shaped webhook payloads. Used by
  Malli `:json-schema/example` annotations so the OpenAPI spec
  surfaces realistic samples.")

(def CheckCompletedObject
  {:id "9b6e8d8f-5b9a-4f4f-9f4d-1234567890ab"
   :status "complete"
   :result "clear"
   :completed_at_iso8601 "2026-05-02T12:00:00Z"})

(def CheckCompletedPayload
  {:resource_type "check"
   :action "check.completed"
   :object CheckCompletedObject})

(def CheckCompletedWebhook {:payload CheckCompletedPayload})
