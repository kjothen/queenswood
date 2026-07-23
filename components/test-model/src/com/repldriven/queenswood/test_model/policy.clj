(ns com.repldriven.queenswood.test-model.policy)

(defn- no-worse?
  [pre post]
  (>= post pre))

(defn permits-available?
  [{:keys [min improving?] :as _rule} pre post]
  (or (>= post min)
      (and improving?
           (< pre min)
           (no-worse? pre post))))

(defn permits?
  [policies kind pre post]
  (case kind
    :available (let [rule (:available policies)]
                 (or (nil? rule)
                     (permits-available? rule pre post)))
    true))
