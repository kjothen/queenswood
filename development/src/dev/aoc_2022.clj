(ns com.repldriven.mono.dev.aoc-2022)

(comment
  ;; day 1
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[com.rpl.specter :refer [ALL transform]])
  (defn parse-uint [s] (Integer/parseUnsignedInt s))
  (defn process
    [data]
    (->> (string/split data #"\n\n")
         (map string/split-lines)
         (transform [ALL ALL] parse-uint)
         (map (partial apply +))
         (sort >)))
  (defn part-1 [data] (first (process data)))
  (defn part-2 [data] (apply + (take 3 (process data))))
  (let [test-data
        "1000\n2000\n3000\n\n4000\n\n5000\n6000\n\n7000\n8000\n9000\n\n10000"
        input-data (slurp (io/resource "aoc-2022/01/input.dat"))]
    (assert (= 24000 (part-1 test-data)))
    (assert (= 45000 (part-2 test-data)))
    {:part-1 (part-1 input-data) :part-2 (part-2 input-data)}))

(comment
  ;; day 2
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[clojure.set]
           '[com.rpl.specter :refer [ALL transform]])
  (defn parse
    [data]
    (->> (string/split-lines data)
         (map #(string/split % #" "))
         (transform [ALL ALL] (comp char first))))
  (defn decrypt
    [data your-decryptor my-decryptor]
    (map (fn [[you me]] [(get your-decryptor you) (get my-decryptor me)]) data))
  (defn score
    [plays points you me]
    (+ (get points me) (get points (get-in plays [you me]))))
  (defn score-all
    [data score-fn plays points]
    (reduce (fn [res [you me]] (+ res (score-fn plays points you me))) 0 data))
  (let [rps {\A :rock \B :paper \C :scissors \X :rock \Y :paper \Z :scissors}
        ldw {\X :lose \Y :draw \Z :win}
        points {:rock 1 :paper 2 :scissors 3 :lose 0 :draw 3 :win 6}
        play-1 {:rock {:rock :draw :paper :win :scissors :lose}
                :paper {:rock :lose :paper :draw :scissors :win}
                :scissors {:rock :win :paper :lose :scissors :draw}}
        play-2 (reduce-kv #(assoc %1 %2 (clojure.set/map-invert %3)) {} play-1)
        plays (merge-with merge play-1 play-2)
        test-data (parse "A Y\nB X\nC Z")
        input-data (parse (slurp (io/resource "aoc-2022/02/input.dat")))]
    (assert (= 15 (score-all (decrypt test-data rps rps) score plays points)))
    (assert (= 12 (score-all (decrypt test-data rps ldw) score plays points)))
    {:part-1 (score-all (decrypt input-data rps rps) score plays points)
     :part-2 (score-all (decrypt input-data rps ldw) score plays points)}))

(comment
  ;; day 3
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[clojure.set :refer [intersection]]
           '[com.rpl.specter :refer [ALL transform]])
  (defn prioritize
    [c]
    (if (Character/isLowerCase c)
      (+ 1 (- (int c) (int \a)))
      (+ 27 (- (int c) (int \A)))))
  (defn process
    [f data]
    (->> (string/split-lines data)
         f
         (transform [ALL ALL] prioritize)
         (map (partial apply +))
         (apply +)))
  (defn part-1
    [data]
    (->> data
         (map (fn [x] (split-at (/ (count x) 2) x)))
         (map (fn [[x y]] (intersection (set x) (set y))))))
  (defn part-2
    [data]
    (->> data
         (partition 3)
         (map (fn [[x y z]] (intersection (set x) (set y) (set z))))))
  (let
    [test-data
     "vJrwpWtwJgWrhcsFMMfFFhFp\njqHRNqRjqzjGDLGLrsFMfFZSrLrFZsSL
PmmdzqPrVvPwwTWBwg\nwMqvLMZHhHMvwLHjbvcjnnSBnvTQFn\nttgJtRGJQctTZtZT
CrZsJsPPZsGzwwsLwLmpwMDw"
     input-data (slurp (io/resource "aoc-2022/03/input.dat"))]
    (assert (= 157 (process part-1 test-data)))
    (assert (= 70 (process part-2 test-data)))
    {:part-1 (process part-1 input-data) :part-2 (process part-2 input-data)}))

(comment
  ;; day 4
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[com.rpl.specter :refer [ALL transform]])
  (defn parse-uint [s] (Integer/parseUnsignedInt s))
  (defn process
    [filter-fn data]
    (->> (string/split-lines data)
         (map #(next (re-matches #"(\d+)-(\d+),(\d+)-(\d+)" %)))
         (transform [ALL ALL] parse-uint)
         (filter filter-fn)
         count))
  (defn filter-1
    [[x y x' y']]
    (or (and (<= x x') (>= y y')) (and (<= x' x) (>= y' y))))
  (defn filter-2
    [[x y x' y']]
    (or (and (<= x x') (>= y x')) (and (<= x' x) (>= y' x))))
  (let [test-data "2-4,6-8\n2-3,4-5\n5-7,7-9\n2-8,3-7\n6-6,4-6\n2-6,4-8"
        input-data (slurp (io/resource "aoc-2022/04/input.dat"))]
    (assert (= 2 (process filter-1 test-data)))
    (assert (= 4 (process filter-2 test-data)))
    {:part-1 (process filter-1 input-data)
     :part-2 (process filter-2 input-data)}))

(comment
  ;; day 5
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[clojure.set :refer [intersection]]
           '[com.rpl.specter :refer [ALL transform]])
  (def input-data (slurp (io/resource "aoc-2022/05/input.dat")))
  (def test-data
    "    [D]
[N] [C]
[Z] [M] [P]
 1   2   3

move 1 from 2 to 1
move 3 from 1 to 3
move 2 from 2 to 1
move 1 from 1 to 2")
  (defn repeat-str [n s] (str (apply str (repeat n s))))
  (def stacks-str (first (string/split test-data #"\n\n")))
  (def instructions-str (second (string/split test-data #"\n\n")))
  (def stack-idx
    (remove string/blank?
            (-> stacks-str
                string/split-lines
                last
                (string/split #" "))))
  (def stack-cnt (count stack-idx))
  (def stack-width (* stack-cnt 4))
  (def padded-stacks
    (mapv #(str % (repeat-str (- stack-width (count %)) " "))
          (-> stacks-str
              string/split-lines
              butlast)))
  (apply
   map
   vector
   (transform [ALL ALL] #(apply str %) (mapv #(partition 4 %) padded-stacks)))
  (let [[stack-str instruction-str] (string/split test-data #"\n\n")]
    stack-str))
