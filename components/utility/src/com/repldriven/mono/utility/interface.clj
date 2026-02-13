(ns com.repldriven.mono.utility.interface
  (:require
    [com.repldriven.mono.utility.collections :as util.collections]
    [com.repldriven.mono.utility.string :as util.string]))

;; Collection utilities
(def deep-merge util.collections/deep-merge)

;; String utilities
(def string->stream util.string/string->stream)
(def resolve-source util.string/resolve-source)
(def prop-seq->kw-map util.string/prop-seq->kw-map)

(def yaml-collections->edn-collections
  util.collections/yaml-collections->edn-collections)
(def keys->strs util.collections/keys->strs)
