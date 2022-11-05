(ns com.repldriven.mono.pubsub.pulsar.client
  (:refer-clojure :exclude [send])
  (:import (java.util.concurrent TimeUnit)
           (org.apache.pulsar.client.api Consumer Message Producer)))

(defn send
  ([^Producer producer data]
   (.. producer (send data)))
  ([^Producer producer data opts]
   (.. producer newMessage (loadConf opts) (value data) send)))

(defn send-async
  ([^Producer producer data]
   (.. producer (sendAsync data)))
  ([^Producer producer data opts]
   (.. producer newMessage (loadConf opts) (value data) sendAsync)))

(defn ^Message receive
  ([^Consumer consumer]
   (.. consumer receive))
  ([^Consumer consumer timeout-ms]
   (.. consumer (receive timeout-ms TimeUnit/MILLISECONDS))))
