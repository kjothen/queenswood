(ns com.repldriven.mono.bank-bank.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn check-sole-membership
  [user-id existing]
  (when (seq existing)
    (error/reject :membership/already-exists
                  {:message "User already belongs to a bank"
                   :user-id user-id
                   :bank-id (:bank-id (first existing))})))

(defn new-bank
  [bank-name bank-status sort-code company-binding policies]
  (let-nom>
    [_ (policy/check-capability policies
                                :bank
                                {:action :bank-action-create
                                 :status bank-status})
     _ (when (and company-binding
                  (not= "active" (:company-status company-binding)))
         (error/reject
          :onboarding/company-not-active
          {:message "Only an active company can be bound to a bank"
           :company-number (:company-number company-binding)
           :company-status (:company-status company-binding)}))]
    (let [now (utility/now)]
      (utility/assoc-some {:bank-id (utility/generate-id "bnk")
                           :name bank-name
                           :status bank-status
                           :sort-code sort-code
                           :created-at now
                           :updated-at now}
                          :company-binding
                          company-binding))))
