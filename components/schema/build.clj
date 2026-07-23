(ns build
  (:require
    [com.repldriven.queenswood.build.proto :as proto]))

(defn gen-proto [opts] (proto/gen-proto opts))
