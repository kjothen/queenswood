(ns com.repldriven.mono.bank-api.onboarding.handlers
  (:require
    [com.repldriven.mono.bank-api.company-registries.lookup :as lookup]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-membership.interface :as memberships]
    [com.repldriven.mono.bank-bank.interface :as banks]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.string :as str]))

(def ^:private default-status :bank-status-test)
(def ^:private default-tier "micro")
(def ^:private default-currencies ["GBP"])

(defn- office->string
  "Join the non-blank registered-office address lines into one string."
  [{:keys [address-line-1 locality postal-code country]}]
  (->> [address-line-1 locality postal-code country]
       (remove str/blank?)
       (str/join ", ")))

(defn- ->binding
  "Snapshot the confirmed company into the bank's company-binding shape."
  [registry company]
  (let [office (office->string (:registered-office-address company))]
    (util/assoc-some
     {:registry registry
      :company-number (:company-number company)}
     :company-name (:company-name company)
     :company-status (:company-status company)
     :type (:type company)
     :jurisdiction (:jurisdiction company)
     :date-of-creation (:date-of-creation company)
     :registered-office-address (when-not (str/blank? office) office))))

(defn- run-onboard
  "Returns either the response body (on success) or an anomaly. The
  User row has already been upserted by the auth interceptor. Looks up
  and confirms the company, then provisions the bank bound to it plus
  the owner membership."
  [request txn user identity-provider audience registry company-number
   bank-name]
  (let-nom> [existing (memberships/list-by-user txn (:user-id user))]
    (if (seq existing)
      (error/reject :membership/already-exists
                    {:message "User already belongs to a bank"
                     :user-id (:user-id user)
                     :bank-id (:bank-id (first existing))})
      (let-nom> [company (lookup/find-company request registry company-number)
                 _ (when-not (= "active" (:company-status company))
                     (error/reject
                      :onboarding/company-not-active
                      {:message
                       "Only an active company can be bound to a bank"
                       :company-number company-number
                       :company-status (:company-status company)}))
                 bank-result (banks/new-bank
                              txn
                              bank-name
                              default-status
                              default-tier
                              default-currencies
                              {:identity-provider identity-provider
                               :audience audience
                               :company-binding (->binding registry company)})
                 {:keys [bank client-secret]} bank-result
                 {:keys [bank-id]} bank
                 membership (memberships/new-membership
                             txn
                             {:user-id (:user-id user)
                              :bank-id bank-id
                              :role :role-owner})]
        {:user user
         :bank (-> bank
                   (assoc :client-secret client-secret)
                   (update :company-binding #(when % (into {} %))))
         :membership membership}))))

(defn onboard
  "First-sign-in onboarding: looks up and confirms a UK Companies House
  company, provisions a new customer Bank bound to that legal entity,
  and binds the signed-in user to it as the owner. The User row has
  already been created by the auth interceptor's upsert. Returns 409 if
  the user already belongs to a bank — the MVP is one user, one bank."
  [request]
  (let [{:keys [record-db record-store identity-provider auth parameters
                audiences-by-status]}
        request
        {:keys [user]} auth
        {:keys [body]} parameters
        {:keys [registry company-number bank-name]} body
        registry (or registry lookup/supported-registry)
        txn {:record-db record-db :record-store record-store}
        audience (get audiences-by-status default-status)
        result (run-onboard request
                            txn
                            user
                            identity-provider
                            audience
                            registry
                            company-number
                            bank-name)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 201 :body result})))
