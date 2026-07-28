(ns com.repldriven.queenswood.api.onboarding.handlers
  (:require
    [com.repldriven.queenswood.api.bank.commands :as bank-commands]
    [com.repldriven.queenswood.api.company-registries.queries :as companies]
    [com.repldriven.queenswood.api.errors :as errors]

    [com.repldriven.mono.error.interface :as error]
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

(defn onboard
  "First-sign-in onboarding: looks up a UK Companies House company,
  then dispatches a create-bank command that provisions the customer
  Bank bound to that legal entity and the owner membership in one
  transaction. The User row has already been created by the auth
  interceptor's upsert. Returns 409 if the user already belongs to a
  bank — the MVP is one user, one bank. The fast path reads the
  interceptor-loaded memberships; the processor re-checks inside its
  transaction, so a racing double-submit still creates one bank."
  [request]
  (let [{:keys [auth parameters audiences-by-status]} request
        {:keys [user memberships]} auth
        {:keys [body]} parameters
        {:keys [registry company-number bank-name]} body]
    (if (seq memberships)
      (errors/anomaly->response
       (error/reject :membership/already-exists
                     {:message "User already belongs to a bank"
                      :user-id (:user-id user)
                      :bank-id (:bank-id (first memberships))}))
      (let [lookup (companies/lookup request registry company-number)]
        (if (not= 200 (:status lookup))
          lookup
          (let [company (:body lookup)
                result (bank-commands/send-create-bank
                        request
                        {:name bank-name
                         :status default-status
                         :tier default-tier
                         :currencies default-currencies
                         :audience (get audiences-by-status default-status)
                         :company-binding (->binding (:registry-id company)
                                                     company)
                         :membership {:user-id (:user-id user)
                                      :role :role-owner}})]
            (if (not= 200 (:status result))
              result
              (let [{:keys [bank-id membership]} (:body result)
                    bank (bank-commands/bank-with-secret request bank-id)]
                (if (error/anomaly? bank)
                  (errors/anomaly->response bank)
                  {:status 201
                   :body {:user user
                          :bank bank
                          :membership membership}})))))))))
