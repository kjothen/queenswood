(ns com.repldriven.mono.bank-api.organization.examples
  (:require
    [com.repldriven.mono.bank-api.api-key.examples :as
     api-key-examples]
    [com.repldriven.mono.bank-api.balance.examples :as
     balance-examples]
    [com.repldriven.mono.bank-api.cash-account.examples :as
     cash-account-examples]
    [com.repldriven.mono.bank-api.party.examples :as
     party-examples]
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def OrganizationLimitExceeded
  {:value {:title "REJECTED"
           :type "cash-account/limit-max-accounts"
           :status 422
           :detail "Tier limit exceeded for this organization"}})

(def TierNotFound
  {:value {:title "REJECTED"
           :type ":tier/not-found"
           :status 404
           :detail "Tier not found"}})

(def OrganizationNotFound
  {:value {:title "REJECTED"
           :type ":organization/not-found"
           :status 404
           :detail "Organization not found"}})

(def registry
  (examples-registry [#'OrganizationLimitExceeded #'TierNotFound
                      #'OrganizationNotFound]))

(def OrganizationId "org.01kprbmgcj35ptc8npmybhh4s7")

(def Organization
  {:organization-id OrganizationId
   :name "Galactic Bank"
   :type :customer
   :status :test
   :tier-id "tie.01kprbmgcj35ptc8npmybhh4t0"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"
   :party (assoc party-examples/Party :type :organization)
   :accounts [(assoc cash-account-examples/CashAccount
                     :balances
                     [balance-examples/Balance])]
   :api-key api-key-examples/ApiKey})

(def OrganizationList {:organizations [Organization]})

(def CreateOrganizationRequest
  {:name "Galactic Bank"
   :status :test
   :tier-id "tie.01kprbmgcj35ptc8npmybhh4t0"
   :currencies ["GBP"]})

(def CreateOrganizationResponse
  (assoc Organization :api-key-secret api-key-examples/ApiKeySecret))

