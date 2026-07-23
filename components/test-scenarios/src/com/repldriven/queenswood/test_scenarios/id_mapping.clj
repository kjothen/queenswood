(ns com.repldriven.queenswood.test-scenarios.id-mapping)

(def init {:model->real {} :real->model {}})

(defn add
  [mapping model-id real-id]
  (-> mapping
      (assoc-in [:model->real model-id] real-id)
      (assoc-in [:real->model real-id] model-id)))

(defn real
  [mapping model-id]
  (get-in mapping [:model->real model-id]))

(defn model
  [mapping real-id]
  (get-in mapping [:real->model real-id]))
