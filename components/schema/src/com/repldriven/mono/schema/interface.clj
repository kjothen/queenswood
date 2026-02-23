(ns com.repldriven.mono.schema.interface
  (:require
    [com.repldriven.mono.schema.addressbook :as addressbook]
    [com.repldriven.mono.schema.person :as person]))

;; Person-PhoneType

(def Person-PhoneType-default person/Person-PhoneType-default)
(def Person-PhoneType-val2label person/Person-PhoneType-val2label)
(def Person-PhoneType-label2val person/Person-PhoneType-label2val)

;; Person

(def Person-defaults person/Person-defaults)
(def new-Person person/new-Person)
(def pb->Person person/pb->Person)

;; Person-PhoneNumber

(def Person-PhoneNumber-defaults person/Person-PhoneNumber-defaults)
(def new-Person-PhoneNumber person/new-Person-PhoneNumber)
(def pb->Person-PhoneNumber person/pb->Person-PhoneNumber)

;; AddressBook

(def AddressBook-defaults addressbook/AddressBook-defaults)
(def new-AddressBook addressbook/new-AddressBook)
(def pb->AddressBook addressbook/pb->AddressBook)
