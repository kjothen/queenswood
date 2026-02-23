(ns com.repldriven.mono.schema.interface
  (:require
    [com.repldriven.mono.schema.addressbook :as addressbook]
    [com.repldriven.mono.schema.person :as person]

    [protojure.protobuf :as proto]))

;; Person-PhoneType

(def Person-PhoneType-default person/Person-PhoneType-default)
(def Person-PhoneType-val2label person/Person-PhoneType-val2label)
(def Person-PhoneType-label2val person/Person-PhoneType-label2val)

;; Person defaults

(def Person-defaults person/Person-defaults)
(def Person-PhoneNumber-defaults person/Person-PhoneNumber-defaults)

;; AddressBook defaults

(def AddressBook-defaults addressbook/AddressBook-defaults)

;; Decode (bytes -> map)

(def pb->Person person/pb->Person)
(def pb->Person-PhoneNumber person/pb->Person-PhoneNumber)
(def pb->AddressBook addressbook/pb->AddressBook)

;; Encode (map -> bytes)

(defn Person->pb [m] (proto/->pb (person/new-Person m)))
(defn Person-PhoneNumber->pb [m] (proto/->pb (person/new-Person-PhoneNumber m)))
(defn AddressBook->pb [m] (proto/->pb (addressbook/new-AddressBook m)))
