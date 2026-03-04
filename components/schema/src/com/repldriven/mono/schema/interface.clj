(ns com.repldriven.mono.schema.interface
  (:require
    [com.repldriven.mono.schema.accounts :as accounts]
    [com.repldriven.mono.schema.keys :as keys]
    [com.repldriven.mono.schema.organizations :as organizations]
    [com.repldriven.mono.schema.persons :as persons]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schema.accounts AccountProto$Account)
    (com.repldriven.mono.schema.keys ApiKeyProto$ApiKey)
    (com.repldriven.mono.schema.organizations OrganizationProto$Organization)
    (com.repldriven.mono.schema.persons PersonProto$Person)))

(def pb->Person persons/pb->Person)
(defn Person->pb [m] (proto/->pb (persons/new-Person m)))
(defn Person->java [m] (PersonProto$Person/parseFrom (Person->pb m)))

(def pb->ApiKey keys/pb->ApiKey)
(defn ApiKey->pb [m] (proto/->pb (keys/new-ApiKey m)))
(defn ApiKey->java [m] (ApiKeyProto$ApiKey/parseFrom (ApiKey->pb m)))

(def pb->Organization organizations/pb->Organization)
(defn Organization->pb [m] (proto/->pb (organizations/new-Organization m)))
(defn Organization->java
  [m]
  (OrganizationProto$Organization/parseFrom (Organization->pb m)))

(def pb->Account accounts/pb->Account)
(defn Account->pb [m] (proto/->pb (accounts/new-Account m)))
(defn Account->java [m] (AccountProto$Account/parseFrom (Account->pb m)))
