(ns com.repldriven.mono.bank-api.parties.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def PartyNotFound
  {:value {:title "REJECTED"
           :type "party/not-found"
           :status 404
           :detail "Party not found"}})

(def registry (examples-registry [#'PartyNotFound]))

(def Party
  {:party-id "py_01JMABC123"
   :type :person
   :display-name "Jane Doe"
   :status :pending
   :created-at "2025-01-01T00:00:00Z"
   :updated-at "2025-01-01T00:00:00Z"})

(def PartyId (:party-id Party))

(def PartyList {:parties [Party]})

(def CreatePartyRequest
  {:type "PERSON"
   :display-name "Jane Doe"
   :given-name "Jane"
   :family-name "Doe"
   :date-of-birth 19900115
   :nationality "GB"})

(def CreatePartyResponse Party)
