(ns com.repldriven.mono.spec-spec.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

(def non-empty-string? (s/and string? (complement str/blank?)))
