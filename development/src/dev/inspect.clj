(ns dev.inspect
  "REPL-only inspection helpers for a started system. Needs the :jol
  alias on the classpath for the heap-size fns. Reads donut.system's
  :donut.system/instances key directly (a plain keyword, no donut
  dependency)."
  (:import
    (org.openjdk.jol.info GraphLayout)))

(defn mem-size
  "Retained heap footprint of the object graph reachable from x.
  JOL dedups by identity, so shared structure is counted once and
  cycles are safe — unlike clojure.core/pr. Returns bytes + objects."
  [x]
  (let [gl (GraphLayout/parseInstance (into-array Object [x]))]
    {:bytes (.totalSize gl) :objects (.totalCount gl)}))

(defn mem-footprint
  "Per-class breakdown string (count, total bytes) of the graph
  reachable from x — shows what actually dominates."
  [x]
  (.toFootprint (GraphLayout/parseInstance (into-array Object [x]))))

(defn data-footprint
  "Total UNIQUE retained heap of a started system's data, counting
  structure shared across components only once. Every component
  instance is handed to a single GraphLayout as a root (JOL dedups
  across roots), so the shared serde / FDB metadata aren't double
  counted the way summing instances-by-size would. Instances JOL
  can't size (infra holding hidden-class lambdas) are skipped, so this
  is the data footprint, not the whole live process. Returns bytes +
  objects + how many instances were measured vs skipped."
  [sys]
  (let [insts (mapcat vals (vals (:donut.system/instances sys)))
        measurable (filter (fn [i]
                             (try (GraphLayout/parseInstance
                                   (into-array Object [i]))
                                  true
                                  (catch Throwable _ false)))
                           insts)
        gl (GraphLayout/parseInstance (into-array Object measurable))]
    {:bytes (.totalSize gl)
     :objects (.totalCount gl)
     :measured (count measurable)
     :skipped (- (count insts) (count measurable))}))

(defn instances-by-size
  "Rank a started system's component instances by retained bytes,
  heaviest first. GraphLayout follows every reference, so a component
  holding an FDB/Jetty/Pulsar handle reaches JDK lambdas with captured
  fields (hidden classes) that JOL cannot size on modern JDKs; those
  are reported as :unmeasurable instead of aborting the ranking. To
  size your own data, call mem-size on a specific subtree, e.g.
  (mem-size (get-in sys [:donut.system/instances :avro :serde]))."
  [sys]
  (->> (:donut.system/instances sys)
       (mapcat (fn [[grp comps]]
                 (map (fn [[c inst]]
                        [[grp c]
                         (try (:bytes (mem-size inst))
                              (catch Throwable _ :unmeasurable))])
                      comps)))
       (sort-by (fn [[_ v]] (if (number? v) v -1)) >)))
