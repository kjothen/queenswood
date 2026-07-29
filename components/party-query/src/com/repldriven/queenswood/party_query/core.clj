(ns com.repldriven.queenswood.party-query.core
  (:require
    [com.repldriven.queenswood.party-query.store :as store]

    [com.repldriven.queenswood.person-identification.interface :as person-id]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn get-party
  [txn bank-id party-id]
  (let-nom> [party (store/get-party txn bank-id party-id)]
    (or party
        (error/reject :party/not-found
                      {:message "Party not found"
                       :bank-id bank-id
                       :party-id party-id}))))

(defn get-party-detail
  [txn bank-id party-id
   {:keys [person-identification address national-identifier]}]
  ;; Only the summary party read is load-bearing (not-found ⇒ 404). The
  ;; enrichment is opt-in per `embed` flag and best-effort: a failure
  ;; there must never break the party read the rest of the system (and
  ;; the IDV-status poll) depends on. Person identification carries both
  ;; the identity fields and the address, so one read serves both flags.
  (let-nom> [party (get-party txn bank-id party-id)]
    (let [pi (when (or person-identification address)
               (let [r (error/try-nom
                        :party/person-identification-read
                        "Failed to load person identification"
                        (person-id/get-person-identification txn party-id))]
                 (when-not (error/anomaly? r) r)))
          ni (when national-identifier
               (let [r (error/try-nom
                        :party/national-identifier-read
                        "Failed to load national identifier"
                        (store/get-party-national-identifier txn party-id))]
                 (when-not (error/anomaly? r) r)))]
      (cond-> party
              (and person-identification pi)
              (merge (select-keys pi
                                  [:given-name :middle-names :family-name
                                   :date-of-birth :nationality]))

              (and address pi)
              (assoc :address (:address pi))

              ni
              (assoc :national-identifier
                     ;; Stringify the enum `:type` — it leaves via a
                     ;; lenient map with no schema encoder, so hand JSON a
                     ;; plain string rather than a raw keyword.
                     (-> (select-keys ni [:type :value :issuing-country])
                         (update :type
                                 #(cond (keyword? %)
                                        (name %)
                                        (some? %)
                                        (str %)
                                        :else
                                        %))))))))
