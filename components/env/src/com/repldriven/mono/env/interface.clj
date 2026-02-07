(ns com.repldriven.mono.env.interface
  (:require [com.repldriven.mono.env.core :as core]
            [com.repldriven.mono.env.reader.yml :as reader.yml]))

(def edn-reader core/edn-reader)
(def yml-reader core/yml-reader)
(def tag-reader reader.yml/tag-reader)

(defn env
  ([] (core/env))
  ([source] (core/env source))
  ([source profile] (core/env source profile)))

