(ns com.repldriven.queenswood.person-identification.interface
  "Person-identification records — identity-document data
  (given/family/middle names, date of birth, nationality) keyed by
  party-id. Created by bank-party when a person party is registered;
  consumed by bank-idv's party-watcher to initiate identity
  verification. The brick exists to break a would-be cycle between
  bank-party and bank-idv: both bricks need this data, and it lives
  here so neither has to require the other."
  (:require
    [com.repldriven.queenswood.person-identification.domain :as domain]
    [com.repldriven.queenswood.person-identification.store :as store]))

(defn new-person-identification
  "Build a person-identification record. Pure data.

  Args:
  - data: source map with `:given-name`, `:middle-names`,
    `:family-name`, `:date-of-birth`, `:nationality`.
  - party-id: party id this identification is linked to."
  [data party-id]
  (domain/new-person-identification data party-id))

(defn save-person-identification
  "Persist a person-identification record. Returns nil on success
  or an `:error/anomaly` on infra failure.

  Args:
  - txn: FDB handle or open transaction.
  - person-identification: the record map."
  [txn person-identification]
  (store/save-person-identification txn person-identification))

(defn get-person-identification
  "Load a person-identification by party-id. Returns the record
  map, `nil` if not found, or an `:error/anomaly` on infra
  failure.

  Args:
  - txn: FDB handle or open transaction.
  - party-id: party id."
  [txn party-id]
  (store/get-person-identification txn party-id))
