(ns com.repldriven.mono.bank-organization.domain
  (:require
    [com.repldriven.mono.bank-organization.restriction :as restriction]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def allowed-payment-address-schemes [:payment-address-scheme-scan])

(defn new-organization
  "Creates a new Organization record map."
  [org-name org-type org-status tier org-count]
  (let-nom>
    [_ (restriction/limit-max-organizations tier org-type org-count)]
    (let [now (System/currentTimeMillis)]
      {:organization-id (utility/generate-id "org")
       :name org-name
       :type org-type
       :status org-status
       :tier-id (:tier-id tier)
       :created-at now
       :updated-at now})))
