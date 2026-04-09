(ns com.repldriven.mono.bank-api.restriction.examples
  (:require
    [com.repldriven.mono.bank-api.restriction.examples.cash-account
     :as cash-account]
    [com.repldriven.mono.bank-api.restriction.examples.cash-account-product
     :as cash-account-product]
    [com.repldriven.mono.bank-api.restriction.examples.organization
     :as organization]
    [com.repldriven.mono.bank-api.restriction.examples.party
     :as party]))

(def OrganizationPolicies organization/Policies)
(def OrganizationLimits organization/Limits)

(def CashAccountProductPolicies cash-account-product/Policies)
(def CashAccountProductLimits cash-account-product/Limits)

(def CashAccountPolicies cash-account/Policies)
(def CashAccountLimits cash-account/Limits)

(def PartyPolicies party/Policies)
(def PartyLimits party/Limits)

(def registry
  {"OrganizationPolicies" OrganizationPolicies
   "OrganizationLimits" OrganizationLimits
   "CashAccountProductPolicies" CashAccountProductPolicies
   "CashAccountProductLimits" CashAccountProductLimits
   "CashAccountPolicies" CashAccountPolicies
   "CashAccountLimits" CashAccountLimits
   "PartyPolicies" PartyPolicies
   "PartyLimits" PartyLimits})
