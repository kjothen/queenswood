(ns com.repldriven.mono.bank-person-identification.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(defn new-person-identification
  [data party-id]
  (let [{:keys [given-name middle-names family-name
                date-of-birth nationality address]}
        data
        now (utility/now)]
    (utility/assoc-some {:party-id party-id
                         :given-name given-name
                         :family-name family-name
                         :date-of-birth date-of-birth
                         :nationality nationality
                         :address address
                         :created-at now
                         :updated-at now}
                        :middle-names
                        middle-names)))
