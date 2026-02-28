(ns com.repldriven.mono.avro.interface-test
  (:require
    [com.repldriven.mono.avro.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(def user-schema-json
  "{\"type\":\"record\",\"name\":\"User\",\"namespace\":\"com.example\",\"fields\":[{\"name\":\"first_name\",\"type\":\"string\"},{\"name\":\"age\",\"type\":[\"null\",\"int\"],\"default\":null}]}")

(deftest json->schema-test
  (testing "Convert JSON string to Avro schema"
    (let [schema (SUT/json->schema user-schema-json)] (is (some? schema)))))

(deftest serialize-deserialize-test
  (testing "Serialize and deserialize Avro data"
    (let [schema (SUT/json->schema user-schema-json)
          data {:first-name "Alice" :age 30}
          serialized (SUT/serialize schema data)
          deserialized (SUT/deserialize-same schema serialized)]
      (is (bytes? serialized))
      (is (pos? (alength serialized)))
      (is (= "Alice" (get deserialized :first-name)))
      (is (= 30 (get deserialized :age)))))
  (testing "Serialize data with null optional field"
    (let [schema (SUT/json->schema user-schema-json)
          data {:first-name "Bob" :age nil}
          serialized (SUT/serialize schema data)
          deserialized (SUT/deserialize-same schema serialized)]
      (is (= "Bob" (get deserialized :first-name)))
      (is (nil? (get deserialized :age))))))
