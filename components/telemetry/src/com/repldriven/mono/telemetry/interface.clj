(ns com.repldriven.mono.telemetry.interface
  "Public API for telemetry operations."
  (:require
   [com.repldriven.mono.telemetry.core :as core]))

;; Tracing
(defmacro with-span
  "Add a span around code execution.

  Usage:
    (with-span [\"operation-name\" {:attr/key \"value\"}]
      (do-work))"
  [name-and-attrs & body]
  `(core/with-span ~name-and-attrs ~@body))

(defn add-event
  "Add an event to the current span."
  [name attrs]
  (core/add-event name attrs))

(defn set-attribute
  "Set an attribute on the current span."
  [k v]
  (core/set-attribute k v))

;; Metrics
(defn counter
  "Create or get a counter instrument."
  [opts]
  (core/counter opts))

(defn inc-counter!
  "Increment a counter with attributes."
  [counter attrs]
  (core/inc-counter! counter attrs))

(defn add-counter!
  "Add a value to a counter with attributes."
  [counter value attrs]
  (core/add-counter! counter value attrs))
