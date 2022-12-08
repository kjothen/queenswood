(ns com.repldriven.mono.dev.aoc-2022
  (:require [clojure.test :refer [deftest is testing]]))

(comment
  ;; day 1
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[com.rpl.specter :refer [ALL transform]])
  (defn parse-uint [s] (Integer/parseUnsignedInt s))
  (defn part-1 [data] (first data))
  (defn part-2 [data] (apply + (take 3 data)))
  (defn process
    ([data] {:part-1 (process part-1 data) :part-2 (process part-2 data)})
    ([f data]
     (->> (string/split data #"\n\n")
          (map string/split-lines)
          (transform [ALL ALL] parse-uint)
          (map (partial apply +))
          (sort >)
          f)))
  (let [test-data
        "1000\n2000\n3000\n\n4000\n\n5000\n6000\n\n7000\n8000\n9000\n\n10000"
        input-data (slurp (io/resource "aoc-2022/01/input.dat"))]
    (assert (= {:part-1 24000 :part-2 45000} (process test-data)))
    (assert (= {:part-1 70374 :part-2 204610} (process input-data)))))

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
  (defn process
    [data rps ldw plays points]
    {:part-1 (score-all (decrypt data rps rps) score plays points)
     :part-2 (score-all (decrypt data rps ldw) score plays points)})
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
    (assert (= {:part-1 15 :part-2 12}
               (process test-data rps ldw plays points)))
    (assert (= {:part-1 13675 :part-2 14184}
               (process input-data rps ldw plays points)))))

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
  (defn process
    ([data] {:part-1 (process part-1 data) :part-2 (process part-2 data)})
    ([f data]
     (->> (string/split-lines data)
          f
          (transform [ALL ALL] prioritize)
          (map (partial apply +))
          (apply +))))
  (let
    [test-data
     "vJrwpWtwJgWrhcsFMMfFFhFp\njqHRNqRjqzjGDLGLrsFMfFZSrLrFZsSL
PmmdzqPrVvPwwTWBwg\nwMqvLMZHhHMvwLHjbvcjnnSBnvTQFn\nttgJtRGJQctTZtZT
CrZsJsPPZsGzwwsLwLmpwMDw"
     input-data (slurp (io/resource "aoc-2022/03/input.dat"))]
    (assert (= {:part-1 157 :part-2 70} (process test-data)))
    (assert (= {:part-1 7878 :part-2 2760} (process input-data)))))

(comment
  ;; day 4
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[com.rpl.specter :refer [ALL transform]])
  (defn parse-uint [s] (Integer/parseUnsignedInt s))
  (defn filter-1
    [[x y x' y']]
    (or (and (<= x x') (>= y y')) (and (<= x' x) (>= y' y))))
  (defn filter-2
    [[x y x' y']]
    (or (and (<= x x') (>= y x')) (and (<= x' x) (>= y' x))))
  (defn process
    ([data] {:part-1 (process filter-1 data) :part-2 (process filter-2 data)})
    ([filter-fn data]
     (->> (string/split-lines data)
          (map #(next (re-matches #"(\d+)-(\d+),(\d+)-(\d+)" %)))
          (transform [ALL ALL] parse-uint)
          (filter filter-fn)
          count)))
  (let [test-data "2-4,6-8\n2-3,4-5\n5-7,7-9\n2-8,3-7\n6-6,4-6\n2-6,4-8"
        input-data (slurp (io/resource "aoc-2022/04/input.dat"))]
    (assert (= {:part-1 2 :part-2 4} (process test-data)))
    (assert (= {:part-1 503 :part-2 807}))))

(comment
  ;; day 5
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[clojure.set :refer [intersection]]
           '[com.rpl.specter :refer [ALL MAP-VALS LAST select transform]])
  (defn parse-uint [s] (Integer/parseUnsignedInt s))
  (defn repeat-str [n s] (string/join (repeat n s)))
  (defn pad-str [n s pad] (string/join (take n (concat s (repeat pad)))))
  (defn pad-lines [n lines pad] (map #(pad-str n % pad) lines))
  (defn transpose [coll] (apply map vector coll))
  (defn stack-lines->columns
    [lines]
    (let [line-width (inc (apply max (map count lines)))]
      (->> (pad-lines line-width (butlast lines) \space)
           (map (partial partition 4))
           (transform [ALL ALL] #(apply str %))
           transpose
           (map #(remove string/blank? %))
           (transform [ALL ALL] #(ffirst (next (re-matches #"\[(\w)\] " %))))
           (mapv (comp vec reverse)))))
  (defn parse-stack-keys
    [lines]
    (mapv parse-uint (string/split (string/trim (last lines)) #"\s+")))
  (defn parse-stacks
    [lines]
    (zipmap (parse-stack-keys lines) (stack-lines->columns lines)))
  (defn parse-instructions
    [lines]
    (->> lines
         (map #(next (re-matches #"move (\d+) from (\d+) to (\d+)" %)))
         (map (fn [[num from to]] {:from from :to to :num num}))
         (transform [ALL MAP-VALS] parse-uint)))
  (defn part-1
    [instructions stacks]
    (loop [instructions instructions
           stacks stacks
           moves 1]
      (if-not instructions
        stacks
        (let [{:keys [from to num]} (first instructions)
              from-val (last (get stacks from))]
          (recur (if (= num moves) (next instructions) instructions)
                 (-> stacks
                     (update to (fn [stack] (conj stack from-val)))
                     (update from (fn [stack] (vec (drop-last stack)))))
                 (if (= num moves) 1 (inc moves)))))))
  (defn part-2
    [instructions stacks]
    (loop [instructions instructions
           stacks stacks]
      (if-not instructions
        stacks
        (let [{:keys [from to num]} (first instructions)
              from-vals (take-last num (get stacks from))]
          (recur (next instructions)
                 (-> stacks
                     (update to (fn [stack] (vec (concat stack from-vals))))
                     (update from
                             (fn [stack] (vec (drop-last num stack))))))))))
  (defn read-stacks [stacks] (apply str (select [MAP-VALS LAST] (sort stacks))))
  (defn process
    [data]
    (let [[stack-lines instruction-lines] (map string/split-lines
                                               (string/split data #"\n\n"))
          stacks (parse-stacks stack-lines)
          instructions (parse-instructions instruction-lines)]
      {:part-1 (read-stacks (part-1 instructions stacks))
       :part-2 (read-stacks (part-2 instructions stacks))}))
  (let
    [input-data (slurp (io/resource "aoc-2022/05/input.dat"))
     test-data
     "    [D]
[N] [C]
[Z] [M] [P]
 1   2   3

move 1 from 2 to 1
move 3 from 1 to 3
move 2 from 2 to 1
move 1 from 1 to 2"]
    (assert (= {:part-1 "CMZ" :part-2 "MCD"} (process test-data)))
    (assert (= {:part-1 "BZLVHBWQF" :part-2 "TDGJQTZSL"}
               (process input-data)))))

(comment
  ;; day 6
  (require '[clojure.java.io :as io] '[clojure.string :as string])
  (defn marker
    [msg width]
    (+ width
       (reduce (fn [acc msg] (if (apply distinct? msg) (reduced acc) (inc acc)))
               0
               (partition width 1 msg))))
  (assert (= 7 (marker "mjqjpqmgbljsphdztnvjfqwrcgsmlb" 4)))
  (assert (= 5 (marker "bvwbjplbgvbhsrlpgdmjqwftvncz" 4)))
  (assert (= 6 (marker "nppdvjthqldpwncqszvftbrmjlhg" 4)))
  (assert (= 10 (marker "nznrnfrfntjfmvfwmzdfjlvtqnbhcprsg" 4)))
  (assert (= 11 (marker "zcfzfwzzqfrljwzlrfnpqdbhtmscgvjw" 4)))
  (assert (= 19 (marker "mjqjpqmgbljsphdztnvjfqwrcgsmlb") 14))
  (assert (= 23 (marker "bvwbjplbgvbhsrlpgdmjqwftvncz") 14))
  (assert (= 23 (marker "nppdvjthqldpwncqszvftbrmjlhg") 14))
  (assert (= 29 (marker "nznrnfrfntjfmvfwmzdfjlvtqnbhcprsg") 14))
  (assert (= 26 (marker "zcfzfwzzqfrljwzlrfnpqdbhtmscgvjw") 14))
  (let [input-data (slurp (io/resource "aoc-2022/06/input.dat"))]
    (assert (= {:part-1 1034 :part-2 2472}
               {:part-1 (marker input-data 4)
                :part-2 (marker input-data 14)}))))

(comment
  ;; day 7
  (require '[clojure.java.io :as io] '[clojure.string :as string])
  (defn cd
    [path dir]
    (case dir
      "/" ["/"]
      ".." (vec (butlast path))
      (vec (conj path dir))))
  (defn make-file-entry
    [coll]
    {:size (parse-uint (first coll)) :filename (second coll)})
  (defn make-path-keys
    [path]
    (if (= ["/"] path)
      path
      (vec (butlast (interleave path (repeat :children))))))
  (defn lines->tree
    [lines]
    (loop [tree {}
           lines (string/split-lines lines)
           path []]
      (if-not lines
        tree
        (let [line (first lines)
              parts (vec (string/split line #" "))
              cmd? (= "$" (first parts))
              cmd (when cmd? (second parts))
              cmd-args (when cmd? (nthrest cmd 2))
              path' (cond-> path
                      (and cmd? (= "cd" (second parts))) (cd (nth parts 2)))]
          (recur (if (not (or cmd? (= (first parts) "dir")))
                   (do (prn path' parts)
                       (update-in
                        tree
                        (make-path-keys path')
                        (fn [m]
                          (update m
                                  :files
                                  (fn [vs]
                                    (vec (conj vs (make-file-entry parts))))))))
                   tree)
                 (next lines)
                 path')))))
  (defn file-sizes
    [files]
    (reduce (fn [acc file] (+ acc (:size file))) 0 files))
  (defn dir-sizes
    ([dirs]
     (let [res (transient [])]
       (dir-sizes res "" dirs)
       (let [ret (persistent! res)] (apply hash-map (flatten ret)))))
    ([res dir dirs]
     (mapv (fn [[k v]]
             (let [path (string/join (if (< 1 (count dir)) "/" "") [dir k])]
               (conj! res [path (file-sizes (:files v))])
               (when (:children v) (dir-sizes res path (:children v)))))
           dirs)))
  (defn filter-paths
    [s paths]
    (filterv (fn [[k _]] (string/starts-with? k s)) paths))
  (defn subdir-sizes
    [dirs]
    (reduce-kv (fn [m k v] (assoc m k (apply + (vals (filter-paths k dirs)))))
               {}
               dirs))
  (defn process
    [data]
    (let [x (lines->tree data)
          y (dir-sizes x)
          z (subdir-sizes y)
          total-size (get z "/")
          free-space (- 70000000 total-size)
          target (- 30000000 free-space)]
      {:part-1 (apply + (filter #(<= % 100000) (vals z)))
       :part-2 (apply min (filter #(>= % target) (vals z)))}))
  (let
    [test-data
     "$ cd /
$ ls
dir a
14848514 b.txt
8504156 c.dat
dir d
$ cd a
$ ls
dir e
29116 f
2557 g
62596 h.lst
$ cd e
$ ls
584 i
$ cd ..
$ cd ..
$ cd d
$ ls
4060174 j
8033020 d.log
5626152 d.ext
7214296 k"
     input-data (slurp (io/resource "aoc-2022/07/input.dat"))]
    (assert (= {:part-1 1297159 :part-2 3866390} (process input-data)))
    (assert (= {:part-1 95437 :part-2 24933642} (process test-data)))))

(comment
  ;; day 8
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[com.rpl.specter :refer [ALL transform]])
  (defn parse-uint [s] (Integer/parseUnsignedInt s))
  (defn cell [vs m n] (nth (nth vs m) n))
  (defn make-range
    [vs m n view]
    (case view
      :up (reverse (range 0 m))
      :left (reverse (range 0 n))
      :right (range (inc n) (count vs))
      :down (range (inc m) (count vs))))
  (defn make-coll
    [vs m n view]
    (let [r (make-range vs m n view)]
      (case view
        (:up :down) (reduce (fn [res m'] (conj res (cell vs m' n))) [] r)
        (:left :right) (reduce (fn [res n'] (conj res (cell vs m n'))) [] r))))
  ;; part-1
  (defn view-visible?
    [vs m n view]
    (let [v (cell vs m n)
          c (make-coll vs m n view)]
      (reduce (fn [res v'] (if (<= v v') (reduced false) res)) true c)))
  (defn cell-visible?
    [vs m n]
    (or (view-visible? vs m n :up)
        (view-visible? vs m n :left)
        (view-visible? vs m n :right)
        (view-visible? vs m n :down)))
  (defn visibles
    [vs]
    (for [m (range 1 (dec (count vs)))
          n (range 1 (dec (count vs)))
          :when (cell-visible? vs m n)]
      (cell vs m n)))
  ;; part-2
  (defn view-score
    [vs m n view]
    (let [v (cell vs m n)
          c (make-coll vs m n view)]
      (reduce (fn [res v'] (if (> v v') (inc res) (reduced (inc res)))) 0 c)))
  (defn cell-score
    [vs m n]
    (* (view-score vs m n :up)
       (view-score vs m n :left)
       (view-score vs m n :down)
       (view-score vs m n :right)))
  (defn scores
    [vs]
    (for [m (range 1 (dec (count vs)))
          n (range 1 (dec (count vs)))]
      (cell-score vs m n)))
  ;; process
  (defn process
    [data]
    (let [vs (->> (string/split-lines data)
                  (map (partial partition 1))
                  (mapv (comp vec flatten))
                  (transform [ALL ALL] #(parse-uint (str %))))]
      {:part-1 (+ (count (visibles vs)) (- (* 4 (count vs)) 4))
       :part-2 (apply max (scores vs))}))
  (let [test-data "30373
25512
65332
33549
35390"
        input-data (slurp (io/resource "aoc-2022/08/input.dat"))]
    (assert (= {:part-1 21 :part-2 8} (process test-data)))
    (assert (= {:part-1 1690 :part-2 535680} (process input-data)))))

(comment
  ;; day 9
  (require '[clojure.java.io :as io]
           '[clojure.string :as string]
           '[com.rpl.specter :refer [ALL transform]])
  (def test-data "")
  (def input-data (slurp (io/resource "aoc-2022/09/input.dat")))
  (defn process [data] {:part-1 0 :part-2 0})
  (assert (= {:part-1 0 :part-2 0} (process test-data)))
  ;;TODO
)
