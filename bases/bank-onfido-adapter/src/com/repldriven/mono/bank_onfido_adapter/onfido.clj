(ns com.repldriven.mono.bank-onfido-adapter.onfido
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
    [com.repldriven.mono.log.interface :as log]

    [clojure.string :as str]))

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

(defn- full-first-name
  "Onfido applicants have no middle_name field — the standard pattern
  is to concatenate given + middle into `first_name` (space-joined)."
  [first-name middle-names]
  (if (str/blank? middle-names)
    first-name
    (str/trim (str first-name " " middle-names))))

(defn- address->onfido
  "Translate our kebab-case address map into Onfido's snake_case
  applicant address shape. Drops nil entries so the request stays
  tight; the Entrust applicant schema only requires postcode +
  country at this layer (our request schema already enforces the
  stricter set: street, town, postcode, country)."
  [{:keys [flat-number building-number building-name street sub-street
           town state postcode country start-date]}]
  (cond-> {:street street
           :town town
           :postcode postcode
           :country country}
          flat-number
          (assoc :flat_number flat-number)
          building-number
          (assoc :building_number building-number)
          building-name
          (assoc :building_name building-name)
          sub-street
          (assoc :sub_street sub-street)
          state
          (assoc :state state)
          start-date
          (assoc :start_date start-date)))

(defn- create-applicant
  [onfido-url {:keys [first-name middle-names last-name date-of-birth address]}]
  (let [body (cond-> {:first_name (full-first-name first-name middle-names)
                      :last_name last-name
                      :address (address->onfido address)}
                     date-of-birth
                     (assoc :dob date-of-birth))]
    (post (str onfido-url "/v3.6/applicants") body)))

(defn composite-external-id
  "Onfido carries one opaque correlation field per check. The
  adapter packs both `:bank-id` and `:verification-id`
  into it (separated by `|`) so the webhook receiver can look up
  the right IDV record without needing a separate stateful
  adapter store."
  [bank-id verification-id]
  (str bank-id "|" verification-id))

(defn parse-external-id
  "Inverse of `composite-external-id`. Returns
  `{:bank-id ... :verification-id ...}` or nil if `s`
  doesn't look like a composite id."
  [s]
  (when (and s (.contains s "|"))
    (let [[bnk vid] (.split s "\\|" 2)]
      {:bank-id bnk :verification-id vid})))

(defn- create-check
  [onfido-url applicant-id bank-id verification-id]
  (post (str onfido-url "/v3.6/checks")
        {:applicant_id applicant-id
         :report_names ["document" "facial_similarity_photo"]
         :external_id (composite-external-id bank-id verification-id)}))

(defn submit-idv-check
  "Performs the create-applicant + create-check call pair against
  Onfido. Returns the check (or anomaly). Both `:bank-id`
  and `:verification-id` are smuggled to Onfido as the check's
  `:external_id` so the webhook receiver can correlate the result
  back to the originating IDV record."
  [config data]
  (let [{:keys [onfido-url]} config
        {:keys [bank-id verification-id
                first-name middle-names last-name date-of-birth address]}
        data]
    (log/info "Submitting Onfido check"
              {:verification-id verification-id
               :first-name first-name})
    (let [applicant (create-applicant onfido-url
                                      {:first-name first-name
                                       :middle-names middle-names
                                       :last-name last-name
                                       :date-of-birth date-of-birth
                                       :address address})]
      (if (error/anomaly? applicant)
        applicant
        (let [applicant-id (:id (http/res->edn applicant))]
          (create-check onfido-url
                        applicant-id
                        bank-id
                        verification-id))))))
