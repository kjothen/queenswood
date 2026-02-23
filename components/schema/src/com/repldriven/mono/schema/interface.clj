(ns com.repldriven.mono.schema.interface
  (:require
    [com.repldriven.mono.schema.addressbook :as addressbook]
    [com.repldriven.mono.schema.person :as person]

    [protojure.protobuf :as proto]))

(def pb->Person person/pb->Person)
(defn Person->pb [m] (proto/->pb (person/new-Person m)))

(def pb->AddressBook addressbook/pb->AddressBook)
(defn AddressBook->pb [m] (proto/->pb (addressbook/new-AddressBook m)))
