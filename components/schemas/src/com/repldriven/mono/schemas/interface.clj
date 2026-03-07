(ns com.repldriven.mono.schemas.interface
  (:require
    [com.repldriven.mono.schemas.accounts :as accounts]
    [com.repldriven.mono.schemas.keys :as keys]
    [com.repldriven.mono.schemas.organizations :as organizations]
    [com.repldriven.mono.schemas.persons :as persons]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.mono.schemas.accounts AccountProto$Account)
    (com.repldriven.mono.schemas.keys ApiKeyProto$ApiKey)
    (com.repldriven.mono.schemas.organizations OrganizationProto$Organization)
    (com.repldriven.mono.schemas.persons PersonProto$Person)))

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
