(ns com.repldriven.mono.utility.ulid
  "ULID utilities — 26-char Crockford-base32, time-sortable."
  (:import
    (com.github.f4b6a3.ulid UlidCreator)))

(defn monotonic
  "Generate a monotonic ULID as a lowercase 26-char string.

  Monotonic within the same millisecond, so IDs generated in a
  tight loop remain strictly increasing."
  []
  (-> (UlidCreator/getMonotonicUlid)
      .toString
      .toLowerCase))
