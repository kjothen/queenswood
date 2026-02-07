(ns com.repldriven.mono.pubsub.system.core
  (:require
   [com.repldriven.mono.pubsub.system.components :as components]
   [com.repldriven.mono.system.interface :as system]))

(system/defcomponents :pubsub
  {:admin components/admin
   :client components/client
   :consumer components/consumer
   :consumers components/consumers
   :crypto-key-pair-file-reader components/crypto-key-pair-file-reader
   :crypto-key-pair-file-readers components/crypto-key-pair-file-readers
   :crypto-key-pair-generator components/crypto-key-pair-generator
   :crypto-key-reader components/crypto-key-reader
   :crypto-key-readers components/crypto-key-readers
   :namespaces components/namespaces
   :producer components/producer
   :reader components/reader
   :schemas components/schemas
   :tenants components/tenants
   :topics components/topics})
