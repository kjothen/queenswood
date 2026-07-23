(ns com.repldriven.queenswood.membership.interface
  "Memberships join a User to a Bank with a Role. Designed
  for N:M (one User can join many Banks; one Bank has many Users) but
  the MVP onboarding flow only ever creates `:role-owner`
  memberships. Additional roles are reserved in the proto."
  (:require
    [com.repldriven.queenswood.membership.core :as core]))

(defn new-membership
  "Create a membership linking a User to a Bank with a Role.

  Args:
  - txn: FDB transaction or db handle.
  - input: map with `:user-id`, `:bank-id`, and optional
    `:role` (`:role-*` keyword; defaults to `:role-owner`).

  Returns the Membership map or an anomaly."
  [txn input]
  (core/new-membership txn input))

(defn list-by-user
  "List memberships for a given user. Returns a vector (possibly
  empty) of Membership maps, or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - user-id: user id (string)."
  [txn user-id]
  (core/list-by-user txn user-id))

(defn list-by-bank
  "List memberships for a given bank. Returns a vector
  (possibly empty) of Membership maps, or an anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - bank-id: bank id (string)."
  [txn bank-id]
  (core/list-by-bank txn bank-id))

(defn find-by-id
  "Load a Membership by id. Returns the map or a
  `:membership/not-found` rejection anomaly.

  Args:
  - txn: FDB transaction or db handle.
  - membership-id: membership id (string)."
  [txn membership-id]
  (core/find-by-id txn membership-id))
