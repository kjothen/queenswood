(ns com.repldriven.mono.bank-organization.domain
  (:require
    [com.repldriven.mono.bank-organization.restriction :as restriction]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(def allowed-payment-address-schemes [:payment-address-scheme-scan])

(defn new-organization
  "Creates a new Organization record map."
  [org-name org-type tier org-count]
  (let-nom>
    [_ (restriction/limit-max-organizations tier org-type org-count)]
    (let [now (System/currentTimeMillis)]
      {:organization-id (encryption/generate-id "org")
       :name org-name
       :type org-type
       :tier-type (:tier-type tier)
       :status "active"
       :created-at now
       :updated-at now})))
