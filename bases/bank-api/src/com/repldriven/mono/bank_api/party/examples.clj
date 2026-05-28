(ns com.repldriven.mono.bank-api.party.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def PartyNotFound
  {:value {:title "REJECTED"
           :type "party/not-found"
           :status 404
           :detail "Party not found"}})

(def IdentificationRejected
  {:value {:title "REJECTED"
           :type ":party/identification-rejected"
           :status 422
           :detail "Identification rejected for this party"}})

(def registry (examples-registry [#'PartyNotFound #'IdentificationRejected]))

(def Party
  {:bank-id "bnk.01kprbmgcj35ptc8npmybhh4s7"
   :party-id "pty.01kprbmgcj35ptc8npmybhh4s9"
   :type :person
   :display-name "Arthur Phillip Dent"
   :status :pending
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def PartyId (:party-id Party))

(def PartyList {:parties [Party]})

(def Address
  {:building-number "155"
   :street "Country Lane"
   :town "Cottington"
   :postcode "CT12 4XY"
   :country "GBR"})

(def CreatePartyRequest
  {:type :person
   :display-name "Arthur Phillip Dent"
   :given-name "Arthur"
   :middle-names "Phillip"
   :family-name "Dent"
   :date-of-birth "1950-07-27"
   :nationality "GB"
   :address Address
   :national-identifier
   {:type :national-insurance :value "TN000001A" :issuing-country "GB"}})

(def CreatePartyResponse Party)
