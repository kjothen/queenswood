(ns com.repldriven.mono.http-client.interface-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [com.repldriven.mono.error.interface :as err]
            [com.repldriven.mono.http-client.interface :as http]
            [org.httpkit.fake :refer [with-fake-http]]))

(deftest res->body-string-body-no-content-type-test
  (testing "String body without content-type returns the string"
    (is (= "{\"a\":1,\"b\":2}"
           (http/res->body {:body "{\"a\":1,\"b\":2}"})))))

(deftest res->body-string-body-json-content-type-test
  (testing "String body with JSON content-type returns parsed JSON"
    (is (= {"a" 1 "b" 2}
           (http/res->body {:headers {:content-type "application/json"}
                            :body "{\"a\":1,\"b\":2}"})))))

(deftest res->body-byte-array-body-json-content-type-test
  (testing "Byte array body with JSON content-type returns parsed JSON"
    (is (= {"a" 1 "b" 2}
           (http/res->body {:headers {:content-type "application/json"}
                            :body (.getBytes "{\"a\":1,\"b\":2}")})))))

(deftest res->body-string-body-html-content-type-test
  (testing "String body with HTML content-type returns the string"
    (is (= "<html>...</html>"
           (http/res->body {:headers {:content-type "text/html"}
                            :body "<html>...</html>"})))))

(deftest res->body-nil-response-test
  (testing "Nil response returns nil"
    (is (nil? (http/res->body nil)))))

(deftest res->body-anomaly-passthrough-test
  (testing "Anomaly response is passed through unchanged"
    (let [anomaly (err/fail :test/error "Test error")]
      (is (= anomaly (http/res->body anomaly))))))

(deftest with-fake-http-test
  (testing "with-fake-http allows faking HTTP responses"
    (with-fake-http [{:url "http://example.com/api" :method :get}
                     {:status 200
                      :headers {:content-type "application/json"}
                      :body "{\"result\":\"success\"}"}]
      (let [res (http/request {:url "http://example.com/api" :method :get})]
        (is (= 200 (:status res)))
        (is (= {"result" "success"} (http/res->body res)))))))

(deftest with-fake-http-async-test
  (testing "with-fake-http works with async requests"
    (with-fake-http [{:url "http://example.com/async" :method :get}
                     {:status 200
                      :headers {:content-type "application/json"}
                      :body "{\"async\":true}"}]
      (let [p (http/request-async {:url "http://example.com/async" :method :get})
            res @p]
        (is (= 200 (:status res)))
        (is (= {"async" true} (http/res->body res)))))))
