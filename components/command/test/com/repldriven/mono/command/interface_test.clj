(ns com.repldriven.mono.command.interface-test
  (:require
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

(deftest processor-uncaught-throw-yields-failed-reply-test
  (with-test-system
   [sys "classpath:command/application-local-test.yml"]
   (let [bus (system/instance sys [:message-bus :bus])]
     (testing
       "an uncaught throw in process-fn becomes a FAILED reply and
               does not wedge the channel"
       (let [calls (atom 0)
             process-fn (fn [_envelope]
                          (if (= 1 (swap! calls inc))
                            (throw (ex-info "boom" {}))
                            {:status "ACCEPTED" :payload nil}))
             replies (atom [])
             done (promise)]
         (command/process bus
                          process-fn
                          {:command-channel :command
                           :command-response-channel :command-response})
         (message-bus/subscribe bus
                                :command-response
                                (fn [resp]
                                  (swap! replies conj resp)
                                  (when (= 2 (count @replies))
                                    (deliver done true))))
         (message-bus/send bus
                           :command
                           {:command "c" :id "1" :correlation-id "corr-1"})
         (message-bus/send bus
                           :command
                           {:command "c" :id "2" :correlation-id "corr-2"})
         (is (not= ::timeout (deref done 5000 ::timeout))
             "both commands must produce a reply")
         (let [by-corr (into {} (map (juxt :correlation-id identity)) @replies)]
           (is (= "FAILED" (:status (get by-corr "corr-1")))
               "the throwing command yields a FAILED reply, not a hang")
           (is (= "ACCEPTED" (:status (get by-corr "corr-2")))
               "the channel keeps consuming after a throw")))))))
