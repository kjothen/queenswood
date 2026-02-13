(ns com.repldriven.mono.schema-avro.interface-test
  (:require
    [com.repldriven.mono.schema-avro.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(def user-schema-json
  "{\"type\":\"record\",\"name\":\"User\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":[\"null\",\"int\"],\"default\":null}]}")

(deftest json->schema-test
  (testing "Convert JSON string to Avro schema"
    (let [schema (SUT/json->schema user-schema-json)]
      (is (some? schema)))))

(deftest serialize-deserialize-test
  (testing "Serialize and deserialize Avro data"
    (let [schema (SUT/json->schema user-schema-json)
          data {:name "Alice" :age 30}
          serialized (SUT/serialize schema data)
          deserialized (SUT/deserialize-same schema serialized)]
      (is (bytes? serialized))
      (is (pos? (alength serialized)))
      (is (= "Alice" (:name deserialized)))
      (is (= 30 (:age deserialized)))))
  (testing "Serialize data with null optional field"
    (let [schema (SUT/json->schema user-schema-json)
          data {:name "Bob" :age nil}
          serialized (SUT/serialize schema data)
          deserialized (SUT/deserialize-same schema serialized)]
      (is (= "Bob" (:name deserialized)))
      (is (nil? (:age deserialized))))))
