(ns com.repldriven.mono.schema.interface
  (:require
    [com.repldriven.mono.schema.addressbook :as addressbook]
    [com.repldriven.mono.schema.command :as command]
    [com.repldriven.mono.schema.person :as person]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schema.command
     CommandProto$Command
     CommandProto$CommandResponse)
    (com.repldriven.mono.schema.person PersonProto$Person)))

(def pb->Person person/pb->Person)
(defn Person->pb [m] (proto/->pb (person/new-Person m)))
(defn Person->java [m] (PersonProto$Person/parseFrom (Person->pb m)))

(def pb->AddressBook addressbook/pb->AddressBook)
(defn AddressBook->pb [m] (proto/->pb (addressbook/new-AddressBook m)))

(def pb->Command command/pb->Command)
(defn Command->pb [m] (proto/->pb (command/new-Command m)))
(defn Command->java [m] (CommandProto$Command/parseFrom (Command->pb m)))

(def pb->CommandResponse command/pb->CommandResponse)
(defn CommandResponse->pb [m] (proto/->pb (command/new-CommandResponse m)))
(defn CommandResponse->java
  [m]
  (CommandProto$CommandResponse/parseFrom (CommandResponse->pb m)))
