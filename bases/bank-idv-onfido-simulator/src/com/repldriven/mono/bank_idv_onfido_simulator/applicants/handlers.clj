(ns com.repldriven.mono.bank-idv-onfido-simulator.applicants.handlers
  (:require
    [com.repldriven.mono.utility.interface :refer [uuidv7]]))

(defn- now-iso8601 [] (str (java.time.Instant/now)))

(defn create-applicant
  [_config]
  (fn [request]
    (let [{:keys [state parameters]} request
          {:keys [body]} parameters
          id (str (uuidv7))
          applicant (-> body
                        (select-keys [:first_name :last_name :dob])
                        (assoc :id id :created_at (now-iso8601)))]
      (swap! state assoc-in [:applicants id] applicant)
      {:status 201 :body applicant})))

(defn get-applicant
  [_config]
  (fn [request]
    (let [{:keys [state parameters]} request
          id (get-in parameters [:path :id])
          applicant (get-in @state [:applicants id])]
      (if applicant
        {:status 200 :body applicant}
        {:status 404
         :body {:title "NOT_FOUND"
                :type "applicant/not-found"
                :status 404
                :detail (str "No applicant with id: " id)}}))))
