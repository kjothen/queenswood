(ns com.repldriven.mono.bank-scenario-runner.id-mapping
  "Pure side-table mapping synthetic model ids (e.g. `:acct-0`) to
  real production ids (e.g. `\"acc.01k...\"`). Held by the runner for
  the duration of one command-sequence run; reset between runs.

  The dual `:model->real` / `:real->model` views let verbs translate
  arguments into real ids on the way in, and let projections return
  model ids on the way out (so equality with model state is direct).")

(def init
  "Empty mapping. Equivalent to no accounts known."
  {:model->real {} :real->model {}})

(defn add
  "Records `model-id <-> real-id`. Returns the updated mapping."
  [mapping model-id real-id]
  (-> mapping
      (assoc-in [:model->real model-id] real-id)
      (assoc-in [:real->model real-id] model-id)))

(defn real
  "Real id for `model-id`, or nil if unknown."
  [mapping model-id]
  (get-in mapping [:model->real model-id]))

(defn model
  "Model id for `real-id`, or nil if unknown."
  [mapping real-id]
  (get-in mapping [:real->model real-id]))
