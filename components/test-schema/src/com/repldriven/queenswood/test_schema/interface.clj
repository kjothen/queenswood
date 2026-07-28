(ns com.repldriven.queenswood.test-schema.interface
  "A Pet record, for exercising FDB itself rather than any domain.

  `components/fdb` needs a record type to save, scan and query. Using a
  banking record would couple the lowest-level infrastructure brick to
  `components/schema` and the domain it encodes; this keeps that boundary
  clean, and keeps the fixture free to change for FDB's reasons alone."
  (:require
    [com.repldriven.queenswood.test_schemas.pets :as pets]

    [protojure.protobuf :as proto])
  (:import
    (com.repldriven.queenswood.test_schemas.pets PetProto$Pet)))

(def pb->Pet pets/pb->Pet)
(defn Pet->pb [m] (proto/->pb (pets/new-Pet m)))
(defn Pet->java [m] (PetProto$Pet/parseFrom (Pet->pb m)))
