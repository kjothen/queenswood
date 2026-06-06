(ns com.repldriven.mono.bank-policy.limit-test
  (:require
    [com.repldriven.mono.bank-policy.limit :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- amount-max-policy
  "One policy with a single `kind` amount max limit of `value` minor
  units in `currency` over `window`."
  [kind value currency window]
  [{:enabled true
    :limits [{:kind {kind {}}
              :bound {:kind {:max {:aggregate
                                   {:kind {:amount
                                           {:value {:value value
                                                    :currency currency}
                                            :window window}}}}}}
              :reason "cap"}]}])

(defn- amount-request
  [window value currency]
  {:aggregate :amount :window window :value {:value value :currency currency}})

(deftest amount-max-limit-test
  (let [policies (amount-max-policy :outbound-payment 1000000
                                    "GBP" :time-window-instant)]
    (testing "an amount at the cap passes"
      (is (true? (SUT/check
                  policies
                  :outbound-payment
                  (amount-request :time-window-instant 1000000 "GBP")))))
    (testing "an amount over the cap is rejected"
      (let [result (SUT/check
                    policies
                    :outbound-payment
                    (amount-request :time-window-instant 1000001 "GBP"))]
        (is (error/rejection? result))
        (is (= :policy/limit-exceeded (error/kind result)))))
    (testing "a different currency does not match the GBP cap"
      (is (true? (SUT/check
                  policies
                  :outbound-payment
                  (amount-request :time-window-instant 9999999 "EUR")))))
    (testing "a different window does not match the instant cap"
      (is (true? (SUT/check
                  policies
                  :outbound-payment
                  (amount-request :time-window-daily 9999999 "GBP")))))
    (testing "a count request does not match an amount limit"
      (is (true? (SUT/check policies
                            :outbound-payment
                            {:aggregate :count
                             :window :time-window-daily
                             :value 9999999}))))))
