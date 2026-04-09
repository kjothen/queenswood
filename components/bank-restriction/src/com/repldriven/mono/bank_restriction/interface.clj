(ns com.repldriven.mono.bank-restriction.interface
  (:require
    [com.repldriven.mono.bank-restriction.core :as core]
    [com.repldriven.mono.bank-restriction.store :as store]))

(defn default-customer-organization-restrictions
  []
  (core/restrictions "organization/customer"))

(defn default-cash-account-product-restrictions
  []
  (core/restrictions "cash-account-product"))

(defn default-cash-account-restrictions
  []
  (core/restrictions "cash-account"))

(defn default-person-party-restrictions
  []
  (core/restrictions "party/person"))

(defn default-organization-party-restrictions
  []
  (core/restrictions "party/organization"))

(defn new-restrictions
  "Persists a Restrictions record for the given owner.

  opts: {:organization-id ... :policies [...] :limits [...]}"
  [config owner-id opts]
  (store/new-restrictions config owner-id opts))

(defn get-restrictions
  "Returns the Restrictions record for the given owner-id,
  or nil if not found."
  [config owner-id]
  (store/get-restrictions config owner-id))
