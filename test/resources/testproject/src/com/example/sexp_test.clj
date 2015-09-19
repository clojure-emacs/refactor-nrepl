(ns resources.testproject.src.com.example.sexp-test)

(defn foo []
  (let [some :bindings
        more :bindings]
    (println #{some}
             ;; unhelpful comment )
             (prn {"foo" {:qux [#{more}]}}))))
