(ns com.repldriven.queenswood.onfido-webhook.examples)

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
