(ns com.repldriven.mono.external-test-runner.main
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [eftest.runner :as eftest]
            [eftest.report.pretty :as report])
  (:gen-class))

(set! *warn-on-reflection* true)

(def cli-options
  [["-c" "--color-mode MODE" "Color mode (none, light, dark)"
    :default :none
    :parse-fn keyword]
   ["-p" "--project PROJECT" "Project name"
    :default "unknown"]
   ["-v" "--verbose" "Verbose output"
    :default false]
   ["-s" "--skip-meta META" "Skip tests with metadata (e.g., :integration)"
    :parse-fn keyword
    :assoc-fn (fn [m k v] (update m k (fnil conj []) v))]
   ["-f" "--focus-meta META" "Only run tests with metadata (e.g., :integration)"
    :parse-fn keyword
    :assoc-fn (fn [m k v] (update m k (fnil conj []) v))]
   ["-h" "--help" "Show help"]])

(defn- parse-env-meta
  "Parse comma-separated metadata keywords from environment variable"
  [env-var]
  (when-let [meta-str (System/getenv env-var)]
    (when-not (str/blank? meta-str)
      (mapv keyword (str/split meta-str #",")))))

(defn- build-selector
  "Build a test selector function based on skip-meta and focus-meta"
  [skip-meta focus-meta]
  (let [skip-set (set skip-meta)
        focus-set (set focus-meta)]
    (cond
      ;; If focus-meta is specified, only run tests with those tags
      (seq focus-set)
      (fn [test-var]
        (let [test-meta (meta test-var)]
          (some focus-set (keys test-meta))))

      ;; If skip-meta is specified, skip tests with those tags
      (seq skip-set)
      (fn [test-var]
        (let [test-meta (meta test-var)]
          (not (some skip-set (keys test-meta)))))

      ;; Otherwise, run all tests
      :else
      (constantly true))))

(defn- require-test-namespaces
  "Require all test namespaces"
  [test-nses]
  (doseq [ns-sym test-nses]
    (try
      (require ns-sym)
      (catch Exception e
        (println (format "Failed to require namespace %s: %s" ns-sym (.getMessage e)))
        (throw e)))))

(defn- find-test-vars
  "Find all test vars in the specified namespaces"
  [test-nses]
  (require-test-namespaces test-nses)
  (->> test-nses
       (mapcat (fn [ns-sym]
                 (let [ns-obj (find-ns ns-sym)]
                   (->> (ns-publics ns-obj)
                        vals
                        (filter (fn [v] (:test (meta v))))))))
       vec))

(defn- run-test-namespaces
  "Run tests in the specified namespaces using eftest"
  [test-nses {:keys [verbose skip-meta focus-meta]}]
  (when verbose
    (println (format "Running tests in %d namespace%s"
                     (count test-nses)
                     (if (= 1 (count test-nses)) "" "s")))
    (when (seq skip-meta)
      (println "Skipping tests with metadata:" skip-meta))
    (when (seq focus-meta)
      (println "Focusing on tests with metadata:" focus-meta)))

  (let [all-test-vars (find-test-vars test-nses)
        selector (build-selector skip-meta focus-meta)
        filtered-test-vars (filterv selector all-test-vars)]
    (eftest/run-tests filtered-test-vars
                      {:capture-output? false
                       :multithread? :vars
                       :report report/report
                       :test-warn-time 1000})))

(defn- exit-with-results
  "Exit with appropriate code based on test results"
  [{:keys [fail error] :as results}]
  (let [failures (+ (or fail 0) (or error 0))]
    (System/exit (if (zero? failures) 0 1))))

(defn -main
  "Main entry point for external test runner subprocess

  Called by Corfield external-test-runner with positional args:
    color-mode project-name test-namespace [test-namespace ...]

  Supports metadata-based test filtering via environment variables:
  - ENV: SKIP_META=integration,slow FOCUS_META=unit"
  [& args]
  (let [;; Parse positional args from Corfield runner: color-mode project-name ...test-nses
        color-mode (keyword (first args))
        project (second args)
        test-nses (map symbol (drop 2 args))
        ;; Get metadata filtering from environment variables
        env-skip (parse-env-meta "SKIP_META")
        env-focus (parse-env-meta "FOCUS_META")]
    (cond
      (empty? test-nses)
      (do (println "No test namespaces specified")
          (System/exit 1))

      :else
      (let [results (run-test-namespaces test-nses
                                         {:verbose false
                                          :skip-meta env-skip
                                          :focus-meta env-focus})]
        (exit-with-results results)))))
