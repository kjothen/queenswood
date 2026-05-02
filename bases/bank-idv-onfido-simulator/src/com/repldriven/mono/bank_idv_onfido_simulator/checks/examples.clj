(ns com.repldriven.mono.bank-idv-onfido-simulator.checks.examples)

(def CreateCheckRequest
  {:applicant_id "9b6e8d8f-5b9a-4f4f-9f4d-1234567890ab"
   :report_names ["document" "facial_similarity_photo"]})

(def Check
  {:id "ch_aaaa-bbbb-cccc-dddd"
   :applicant_id "9b6e8d8f-5b9a-4f4f-9f4d-1234567890ab"
   :status "in_progress"
   :result nil
   :created_at "2026-05-02T12:00:00Z"})
