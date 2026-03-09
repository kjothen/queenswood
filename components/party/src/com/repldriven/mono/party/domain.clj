(ns com.repldriven.mono.party.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]

    [clojure.string :as str]))

(def ^:private person-identification-keys
  [:given-name :middle-names :family-name
   :date-of-birth :nationality])

(defn new-party
  "Creates a new party map with status pending."
  [data]
  (let [now (System/currentTimeMillis)]
    (-> (apply dissoc data person-identification-keys)
        (assoc :party-id (encryption/generate-id "py")
               :type (keyword (str/lower-case (:type data)))
               :status :pending
               :created-at now
               :updated-at now))))

(defn activate-party
  "Returns party with status active."
  [party]
  (assoc party
         :status :active
         :updated-at (System/currentTimeMillis)))

(defn new-person-identification
  "Creates a person-identification map linked to party-id."
  [data party-id]
  (let [now (System/currentTimeMillis)]
    (-> (select-keys data person-identification-keys)
        (assoc :party-id party-id
               :created-at now
               :updated-at now))))
