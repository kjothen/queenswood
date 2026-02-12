(ns com.repldriven.mono.build.build-test
  (:require
    [com.repldriven.mono.build.build :as SUT]

    [org.corfield.build :as bb]

    [clojure.test :as test :refer [deftest is testing]]
    [clojure.tools.build.api :as b]))

(deftest uber-version-test
  (testing "Version string with default major-minor"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [result (SUT/uber {})] (is (= "0.0.42" (:version result))))))
  (testing "Version string with custom major-minor"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [result (SUT/uber {:major-minor-version "1.2"})]
        (is (= "1.2.42" (:version result))))))
  (testing "Version string with snapshot flag"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [result (SUT/uber {:snapshot true})]
        (is (= "0.0.999-SNAPSHOT" (:version result))))))
  (testing "Snapshot overrides git count"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [result (SUT/uber {:major-minor-version "2.0" :snapshot true})]
        (is (= "2.0.999-SNAPSHOT" (:version result)))))))

(deftest uber-options-test
  (testing "Transitive flag is set"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [result (SUT/uber {})] (is (true? (:transitive result))))))
  (testing "Conflict handlers are configured"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [result (SUT/uber {})
            handlers (:conflict-handlers result)]
        (is (map? handlers))
        (is (= :data-readers (get handlers "^data_readers.clj[cs]?$")))
        (is (= :append (get handlers "^META-INF/services/.*")))
        (is (= :ignore (:default handlers))))))
  (testing "Original options are preserved"
    (with-redefs [b/git-count-revs (constantly "42")
                  bb/clean identity
                  bb/uber identity]
      (let [opts {:lib 'my.lib :main 'my.main}
            result (SUT/uber opts)]
        (is (= 'my.lib (:lib result)))
        (is (= 'my.main (:main result))))))
  (testing "Build pipeline calls clean then uber"
    (let [calls (atom [])
          mock-clean (fn [opts] (swap! calls conj :clean) opts)
          mock-uber (fn [opts] (swap! calls conj :uber) opts)]
      (with-redefs [b/git-count-revs (constantly "42")
                    bb/clean mock-clean
                    bb/uber mock-uber]
        (SUT/uber {})
        (is (= [:clean :uber] @calls))))))

(deftest avro-defaults-test
  (testing "Default avro-tools version"
    (with-redefs [b/delete (constantly nil)
                  b/process (constantly nil)]
      (let [home (System/getenv "HOME")
            command-args (atom nil)]
        (with-redefs [b/process (fn [opts]
                                  (reset! command-args (:command-args opts)))]
          (SUT/avro {})
          (is (some #(re-find #"1\.11\.0" %) @command-args))))))
  (testing "Default source and target directories"
    (with-redefs [b/delete (constantly nil)
                  b/process (constantly nil)]
      (let [command-args (atom nil)]
        (with-redefs [b/process (fn [opts]
                                  (reset! command-args (:command-args opts)))]
          (SUT/avro {})
          (is (some #(= "resources" %) @command-args))
          (is (some #(= "generated" %) @command-args))))))
  (testing "Custom avro-tools version"
    (with-redefs [b/delete (constantly nil)
                  b/process (constantly nil)]
      (let [command-args (atom nil)]
        (with-redefs [b/process (fn [opts]
                                  (reset! command-args (:command-args opts)))]
          (SUT/avro {:avro-tools-version "1.12.0"})
          (is (some #(re-find #"1\.12\.0" %) @command-args))))))
  (testing "Custom source and target directories"
    (with-redefs [b/delete (constantly nil)
                  b/process (constantly nil)]
      (let [command-args (atom nil)]
        (with-redefs [b/process (fn [opts]
                                  (reset! command-args (:command-args opts)))]
          (SUT/avro {:src "my-schemas" :target "my-generated"})
          (is (some #(= "my-schemas" %) @command-args))
          (is (some #(= "my-generated" %) @command-args))))))
  (testing "Target directory is deleted before compilation"
    (let [deleted-path (atom nil)]
      (with-redefs [b/delete (fn [opts] (reset! deleted-path (:path opts)))
                    b/process (constantly nil)]
        (SUT/avro {:target "my-target"})
        (is (= "my-target" @deleted-path)))))
  (testing "Command includes java, jar path, and avro compile schema"
    (with-redefs [b/delete (constantly nil)
                  b/process (constantly nil)]
      (let [command-args (atom nil)]
        (with-redefs [b/process (fn [opts]
                                  (reset! command-args (:command-args opts)))]
          (SUT/avro {})
          (is (= "java" (first @command-args)))
          (is (some #(= "-jar" %) @command-args))
          (is (some #(= "compile" %) @command-args))
          (is (some #(= "schema" %) @command-args)))))))
