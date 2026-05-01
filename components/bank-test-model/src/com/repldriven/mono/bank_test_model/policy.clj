(ns com.repldriven.mono.bank-test-model.policy
  "Hand-written pure-function re-implementation of the policy rules
  the model needs to evaluate. Mirrors what `bank-policy` enforces in
  production but never imports from it.

  Phase 1 covers a single rule: `:available >= 0` with the lenient
  `:improving?` allowance — out-of-bound `post` is permitted iff
  `pre` was already out-of-bound and `post` is no worse than `pre`.")

(defn- no-worse?
  "True when `post` is no worse than `pre` for a min-bound (larger
  is better)."
  [pre post]
  (>= post pre))

(defn permits-available?
  "Evaluates the available-balance rule on a candidate (pre, post)
  transition. Returns true when the rule allows the move."
  [{:keys [min improving?] :as _rule} pre post]
  (or (>= post min)
      (and improving?
           (< pre min)
           (no-worse? pre post))))

(defn permits?
  "Top-level model policy check. Returns true when the named `kind`
  rule permits the transition. Unknown kinds are permissive."
  [policies kind pre post]
  (case kind
    :available (let [rule (:available policies)]
                 (or (nil? rule)
                     (permits-available? rule pre post)))
    true))
