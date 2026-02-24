(ns com.repldriven.mono.message-bus.protocol (:refer-clojure :exclude [send]))

(defprotocol Producer
  (send [this message]))

(defprotocol Consumer
  (subscribe [this handler-fn])
  (unsubscribe [this]))
