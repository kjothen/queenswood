(ns com.repldriven.mono.bank-api.parties.components
  (:require
    [com.repldriven.mono.bank-api.parties.examples :as examples]

    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def PartyId [:string {:title "PartyId" :json-schema/example examples/PartyId}])

(def Party
  [:map
   {:json-schema/example examples/Party}
   [:party-id [:ref "PartyId"]]
   [:type [:enum :person :party-type-unknown]]
   [:display-name string?]
   [:status
    [:enum :pending :active :suspended :closed
     :party-status-unknown]]
   [:created-at {:optional true} [:maybe string?]]
   [:updated-at {:optional true} [:maybe string?]]])

(def CreatePartyRequest
  [:map
   {:json-schema/example examples/CreatePartyRequest}
   [:type string?]
   [:display-name string?]
   [:given-name string?]
   [:middle-names {:optional true} [:maybe string?]]
   [:family-name string?]
   [:date-of-birth int?]
   [:nationality string?]])

(def CreatePartyResponse [:ref "Party"])

(def PartyList
  [:map
   {:json-schema/example examples/PartyList}
   [:parties
    [:vector [:ref "Party"]]]
   [:links {:optional true}
    [:map
     [:next {:optional true} string?]
     [:prev {:optional true} string?]]]])

(def registry
  (components-registry [#'PartyId #'Party #'CreatePartyRequest
                        #'CreatePartyResponse #'PartyList]))
