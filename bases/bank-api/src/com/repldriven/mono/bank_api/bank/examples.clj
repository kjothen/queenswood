(ns com.repldriven.mono.bank-api.bank.examples
  (:require
    [com.repldriven.mono.bank-api.balance.examples :as
     balance-examples]
    [com.repldriven.mono.bank-api.cash-account.examples :as
     cash-account-examples]
    [com.repldriven.mono.bank-api.party.examples :as
     party-examples]
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def BankLimitExceeded
  {:value {:title "REJECTED"
           :type "cash-account/limit-max-accounts"
           :status 422
           :detail "Tier limit exceeded for this bank"}})

(def BankNotFound
  {:value {:title "REJECTED"
           :type ":bank/not-found"
           :status 404
           :detail "Bank not found"}})

(def registry (examples-registry [#'BankLimitExceeded #'BankNotFound]))

(def BankId "bnk.01kprbmgcj35ptc8npmybhh4s7")

(def ClientSecret "k7DqGZ-Wt0aIqcPyQs8FdVx3y9rNJ4hLp1m6BvE-AtQ")

(def Bank
  {:bank-id BankId
   :name "Galactic Bank"
   :status :test
   :sort-code "000001"
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"
   :party (assoc party-examples/Party :type :organization)
   :accounts [(assoc cash-account-examples/CashAccount
                     :balances
                     [balance-examples/Balance])]
   :client-id BankId})

(def BankList {:banks [Bank]})

(def CreateBankRequest
  {:name "Galactic Bank" :status :test :tier "micro" :currencies ["GBP"]})

(def CompanyBinding
  {:registry "uk-companies-house"
   :company-number "SC998137"
   :company-name "SIRIUS CYBERNETICS CORPORATION LTD"
   :company-status "active"
   :type "ltd"
   :jurisdiction "england-wales"
   :date-of-creation "2009-02-11"
   :registered-office-address
   "42 Improbability Way, London, QZ1 9ZX, United Kingdom"})

(def CreateBankResponse
  (assoc Bank :client-secret ClientSecret :company-binding CompanyBinding))
