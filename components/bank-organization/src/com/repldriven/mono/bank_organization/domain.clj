(ns com.repldriven.mono.bank-organization.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def allowed-payment-address-schemes [:payment-address-scheme-scan])

(defn new-organization
  "Creates a new Organization record map. Returns the
  organization map or an unauthorized anomaly when `policies`
  deny creation or report a violated limit."
  [org-name org-type org-status aggregates policies]
  (let-nom>
    [_ (policy/check-capability policies
                                :organization
                                {:action :organization-action-create
                                 :type org-type
                                 :status org-status})
     _ (policy/check-limit policies
                           :organization
                           {:aggregate :count
                            :window :instant
                            :type org-type
                            :status org-status
                            :value (inc (get-in aggregates
                                                [:organization #{:type}]))})]
    (let [now (System/currentTimeMillis)]
      {:organization-id (utility/generate-id "org")
       :name org-name
       :type org-type
       :status org-status
       :created-at now
       :updated-at now})))
