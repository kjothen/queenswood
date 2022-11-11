(ns com.repldriven.mono.pubsub.pulsar.topics
  (:require [clojure.string :as string]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.pubsub.pulsar.schemas :as schemas])
  (:import (org.apache.pulsar.client.admin PulsarAdmin Topics)
           (org.apache.pulsar.common.naming TopicName)))

(defn- create-topic-schema
  [^PulsarAdmin admin fully-qualified-topic-name schema]
  (log/info "Creating schema for topic:" fully-qualified-topic-name schema)
  (.createSchema (.schemas admin) fully-qualified-topic-name schema))

(defn- create
  [^PulsarAdmin admin fully-qualified-topic-name &
   {:keys [partitions schema] :or {partitions 1}}]
  (let [^TopicName topic-name (TopicName/get fully-qualified-topic-name)
        tenant-name (.getTenant topic-name)
        fully-qualified-namespace-name (.getNamespace topic-name)
        namespace-name (last (string/split fully-qualified-namespace-name
                                           #"/"))]
    (let [^Topics topics (.topics admin)
          domain-name (.getDomain topic-name)
          topic-names
          (.getList topics fully-qualified-namespace-name domain-name)]
      (when-not (contains? (set topic-names) fully-qualified-topic-name)
        (log/info "Creating topic:" fully-qualified-topic-name)
        (.createPartitionedTopic topics fully-qualified-topic-name partitions)
        (when (some? schema)
          (create-topic-schema admin fully-qualified-topic-name schema))))))

(defn create-topics
  [{:keys [^PulsarAdmin admin schemas topics]}]
  (log/info "Ensure pulsar topics exist:" topics)
  (doall
   (mapv (fn [{:keys [topic] :as opts}]
           (let [resolved-opts
                 (update opts :schema #(schemas/resolve-payload schemas %))]
             (create admin topic (dissoc resolved-opts :topic))))
         topics)))
