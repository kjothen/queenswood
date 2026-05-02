(ns com.repldriven.mono.bank-idv-onfido-adapter.onfido
  "Outbound HTTP client for the Onfido API. Implements just the
  surface bank-idv needs to drive identity verification:
  POST /v3.6/applicants and POST /v3.6/checks. The
  `:verification-id` from the originating `submit-idv-check`
  command is smuggled to Onfido as `:external_id` on the check —
  this is a simulator-only field; production callers replacing the
  simulator with the real Onfido SaaS would need a different
  correlation strategy (Onfido `tags`, or a persistent lookup)."
  (:require
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.json.interface :as json]
    [com.repldriven.mono.log.interface :as log]))

(defn- post
  [url body]
  (error/try-nom
   :onfido/http
   "Onfido HTTP call failed"
   (let [res (http/request {:method :post
                            :url url
                            :headers {"Content-Type" "application/json"}
                            :body (json/write-str body)})]
     (if (and (:status res) (>= (:status res) 400))
       (error/fail :onfido/http
                   {:message "Onfido rejected request"
                    :url url
                    :status (:status res)
                    :body (:body res)})
       res))))

(defn- create-applicant
  [onfido-url {:keys [first-name last-name date-of-birth]}]
  (let [body (cond-> {:first_name first-name :last_name last-name}
                     date-of-birth
                     (assoc :dob date-of-birth))]
    (post (str onfido-url "/v3.6/applicants") body)))

(defn composite-external-id
  "Onfido carries one opaque correlation field per check. The
  adapter packs both `:organization-id` and `:verification-id`
  into it (separated by `|`) so the webhook receiver can look up
  the right IDV record without needing a separate stateful
  adapter store."
  [organization-id verification-id]
  (str organization-id "|" verification-id))

(defn parse-external-id
  "Inverse of `composite-external-id`. Returns
  `{:organization-id ... :verification-id ...}` or nil if `s`
  doesn't look like a composite id."
  [s]
  (when (and s (.contains s "|"))
    (let [[org vid] (.split s "\\|" 2)]
      {:organization-id org :verification-id vid})))

(defn- create-check
  [onfido-url applicant-id organization-id verification-id]
  (post (str onfido-url "/v3.6/checks")
        {:applicant_id applicant-id
         :report_names ["document" "facial_similarity_photo"]
         :external_id (composite-external-id organization-id verification-id)}))

(defn submit-idv-check
  "Performs the create-applicant + create-check call pair against
  Onfido. Returns the check (or anomaly). Both `:organization-id`
  and `:verification-id` are smuggled to Onfido as the check's
  `:external_id` so the webhook receiver can correlate the result
  back to the originating IDV record."
  [config data]
  (let [{:keys [onfido-url]} config
        {:keys [organization-id verification-id
                first-name last-name date-of-birth]}
        data]
    (log/info "Submitting Onfido check"
              {:verification-id verification-id
               :first-name first-name})
    (let [applicant (create-applicant onfido-url
                                      {:first-name first-name
                                       :last-name last-name
                                       :date-of-birth date-of-birth})]
      (if (error/anomaly? applicant)
        applicant
        (let [applicant-id (:id (http/res->edn applicant))]
          (create-check onfido-url
                        applicant-id
                        organization-id
                        verification-id))))))
