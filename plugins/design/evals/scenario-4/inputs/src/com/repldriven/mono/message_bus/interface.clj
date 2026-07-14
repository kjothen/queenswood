(ns com.repldriven.mono.message-bus.interface
  "Message-bus abstraction. Producer/Consumer protocols with two
  backends (pulsar for production, an in-process channels backend for
  tests) — production code depends on this interface only, never a
  backend directly.")

(defn publish!
  "Publishes an event. topic names the destination; payload is the
  event body map."
  [producer topic payload]
  ;; delegates to whichever backend the system definition bound
  nil)
