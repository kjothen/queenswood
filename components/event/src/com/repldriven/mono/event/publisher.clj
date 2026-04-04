(ns com.repldriven.mono.event.publisher
  (:require
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.utility.interface :as utility]))

(defn envelope
  "Build a skeleton event envelope.

  Caller should assoc :payload (as bytes or nil) before
  publishing."
  [event-name causation-id correlation-id]
  {:id (str (utility/uuidv7))
   :correlation-id correlation-id
   :event event-name
   :payload nil
   :causation-id causation-id
   :traceparent (telemetry/inject-traceparent)
   :tracestate nil})

(defn publish
  "Publish an event envelope to the event channel.

  Returns the result of message-bus/send, which may be
  an anomaly."
  ([bus envelope] (publish bus envelope {}))
  ([bus envelope opts]
   (let [{:keys [event-channel]
          :or {event-channel :event}}
         opts]
     (message-bus/send bus event-channel envelope))))
