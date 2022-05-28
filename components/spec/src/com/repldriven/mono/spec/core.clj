(ns com.repldriven.mono.spec.core
  (:require [malli.core :as m]))

(def non-empty-string?
  (m/schema [:string {:min 1}]))
