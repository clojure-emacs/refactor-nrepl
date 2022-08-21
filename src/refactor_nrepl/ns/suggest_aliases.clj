(ns refactor-nrepl.ns.suggest-aliases
  "Suggestion of aliases based on these guidelines: https://stuartsierra.com/2015/05/10/clojure-namespace-aliases"
  (:require
   [clojure.edn :as edn]
   [clojure.string :as string]))

(defn test-like-ns-name? [ns-sym]
  (let [ns-str (str ns-sym)]
    (boolean (or (string/ends-with? ns-str "-test")
                 (let [fragments (string/split ns-str #"\.")
                       last-fragment (last fragments)]
                   (or (string/starts-with? last-fragment "t-")
                       (string/starts-with? last-fragment "test-")
                       (some #{"test" "unit" "integration" "acceptance" "functional" "generative"} fragments)))))))

(def readable-as-symbol?
  (memoize
   (fn [s]
     (try
       (symbol? (edn/read-string s))
       (catch Exception _
         false)))))

(defn suggested-aliases [namespace-name]
  (let [fragments (-> namespace-name str (string/split #"\."))
        fragments (into []
                        (comp (remove #{"core" "alpha" "api" "kws"})
                              (map (fn [s]
                                     (-> s
                                         (string/replace "-clj" "")
                                         (string/replace "clj-" "")
                                         (string/replace "-cljs" "")
                                         (string/replace "cljs-" "")
                                         (string/replace "-clojure" "")
                                         (string/replace "clojure-" "")))))
                        fragments)
        fragments (map take-last
                       (range 1 (inc (count fragments)))
                       (repeat (distinct fragments)))
        v (into {}
                (keep (fn [segments]
                        (let [candidate (->> segments (string/join "."))]
                          (when (readable-as-symbol? candidate)
                            [(symbol candidate),
                             [namespace-name]]))))
                fragments)]
    (dissoc v namespace-name)))
