(ns com.repldriven.queenswood.uk-companies-house-adapter.companies-house-test
  "An upstream failure reaches a client as the anomaly's kind, which
  becomes the RFC 9457 problem type and picks the status. Until the
  `>= 400` branch was split, a registry that was down, a registry
  throttling us, and our own malformed request all arrived as the same
  opaque 500 — the upstream status was in the payload but could not
  reach `type`.

  These pin each arm of the split, and pin that the 404 rejection and
  the parse failure are left as they were. They drive `classify`
  directly rather than faking the HTTP layer, because every way of doing
  that redefines a var for the whole JVM and this suite runs in
  parallel."
  (:require
    [com.repldriven.queenswood.uk-companies-house-adapter.companies-house
     :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private company-number "12345678")

(def ^:private json-headers {:content-type "application/json"})

(defn- classifying
  [res]
  (#'SUT/classify company-number res))

(defn- responding
  [status]
  (classifying {:status status :headers json-headers :body "{}"}))

(deftest unreachable-registry-is-unavailable-test
  (testing
    "a transport failure is named for the problem, not the call site,
    and keeps the underlying anomaly as :cause"
    (let [anomaly (classifying (error/fail :http-client/request
                                           {:message "HTTP request failed"}))]
      (is (= :company/unavailable (error/kind anomaly)))
      (is (not (error/rejection? anomaly)))
      (is (some? (:cause (error/payload anomaly)))))))

(deftest upstream-5xx-is-unavailable-test
  (testing "a registry 5xx is retryable and says so"
    (doseq [status [500 502 503 504]]
      (let [anomaly (responding status)]
        (is (= :company/unavailable (error/kind anomaly))
            (str "status " status))
        (is (= status (:status (error/payload anomaly))))))))

(deftest upstream-429-is-rate-limited-test
  (testing
    "being throttled by the registry is its own kind, distinct from the
    registry being down"
    (let [anomaly (responding 429)]
      (is (= :company/rate-limited (error/kind anomaly)))
      (is (not (error/rejection? anomaly)))
      (is (= 429 (:status (error/payload anomaly)))))))

(deftest remaining-4xx-keeps-the-call-site-name-test
  (testing
    "our own bad request or credentials is not caller-actionable, so it
    keeps :company/http and carries the detail"
    (doseq [status [400 401 403 422]]
      (let [anomaly (responding status)]
        (is (= :company/http (error/kind anomaly)) (str "status " status))
        (is (= status (:status (error/payload anomaly))))))))

(deftest not-found-stays-a-rejection-test
  (testing "a 404 already names the problem and already maps to 404"
    (let [anomaly (responding 404)]
      (is (= :company/not-found (error/kind anomaly)))
      (is (error/rejection? anomaly)))))

(deftest unparseable-body-stays-a-parse-failure-test
  (testing
    "a malformed provider response is a defect, not something a caller
    can retry into working"
    (let [anomaly (classifying
                   {:status 200 :headers json-headers :body "not json"})]
      (is (= :company/parse (error/kind anomaly)))
      (is (not (error/rejection? anomaly))))))

(deftest success-returns-the-parsed-body-test
  (testing "a 2xx still parses through to the snake_case map"
    (let [body (classifying {:status 200
                             :headers json-headers
                             :body "{\"company_number\":\"12345678\"}"})]
      (is (not (error/anomaly? body)))
      (is (= company-number (:company_number body))))))
