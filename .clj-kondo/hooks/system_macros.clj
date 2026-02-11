(ns hooks.system-macros
  (:require [clj-kondo.hooks-api :as api]))

(defn with-sysdefs [{:keys [node]}]
  (let [[bindings & body] (rest (:children node))
        [binding-sym] (:children bindings)]
    {:node (api/list-node
            (list*
             (api/token-node 'let)
             (api/vector-node [binding-sym (api/token-node 'nil)])
             body))}))

(defn with-sys [{:keys [node]}]
  (let [[bindings & body] (rest (:children node))
        [binding-sym] (:children bindings)]
    {:node (api/list-node
            (list*
             (api/token-node 'let)
             (api/vector-node [binding-sym (api/token-node 'nil)])
             body))}))
