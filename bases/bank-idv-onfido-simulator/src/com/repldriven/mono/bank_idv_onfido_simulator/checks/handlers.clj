(ns com.repldriven.mono.bank-idv-onfido-simulator.checks.handlers
  (:require
    [com.repldriven.mono.bank-idv-onfido-simulator.webhook :as webhook]

    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :refer [uuidv7]]

    [clojure.string :as str]))

(defn- now-iso8601 [] (str (java.time.Instant/now)))

(defn- result-for-applicant
  "Name-based outcome routing — if the applicant's first_name
  contains `Reject` (case-insensitive), the check resolves
  `consider`, otherwise `clear`. Mirrors the ClearBank simulator's
  trigger-string approach."
  [applicant]
  (let [first-name (or (:first_name applicant) "")]
    (if (str/includes? (str/lower-case first-name) "reject")
      "consider"
      "clear")))

(defn create-check
  [_config]
  (fn [request]
    (let [{:keys [state webhook-delay-ms parameters]} request
          {:keys [body]} parameters
          {:keys [applicant_id]} body
          applicant (get-in @state [:applicants applicant_id])]
      (if-not applicant
        {:status 422
         :body {:title "UNPROCESSABLE_ENTITY"
                :type "applicant/not-found"
                :status 422
                :detail (str "No applicant with id: " applicant_id)}}
        (let [id (str (uuidv7))
              external-id (:external_id body)
              check (cond-> {:id id
                             :applicant_id applicant_id
                             :status "in_progress"
                             :result nil
                             :created_at (now-iso8601)}

                            external-id
                            (assoc :external_id external-id))
              result (result-for-applicant applicant)]
          (swap! state assoc-in [:checks id] check)
          (log/info "Onfido simulator queued check" id
                    "applicant" applicant_id
                    "result" result)
          ;; Async: settle the check + fire the webhook after a
          ;; small delay so callers can model real Onfido latency.
          (future
           (when (pos? (or webhook-delay-ms 0))
             (Thread/sleep webhook-delay-ms))
           (swap! state assoc-in
             [:checks id]
             (assoc check
                    :status "complete"
                    :result result
                    :completed_at_iso8601 (now-iso8601)))
           (webhook/fire-check-completed state id result external-id))
          {:status 201 :body check})))))

(defn get-check
  [_config]
  (fn [request]
    (let [{:keys [state parameters]} request
          id (get-in parameters [:path :id])
          check (get-in @state [:checks id])]
      (if check
        {:status 200 :body check}
        {:status 404
         :body {:title "NOT_FOUND"
                :type "check/not-found"
                :status 404
                :detail (str "No check with id: " id)}}))))
