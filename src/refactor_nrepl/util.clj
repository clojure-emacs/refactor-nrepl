(ns refactor-nrepl.util
  (:require [clojure.tools.namespace.find :refer [find-clojure-sources-in-dir]]
            [clojure.tools.analyzer.ast :refer :all]
            [clojure.tools.namespace.parse :refer [read-ns-decl]])
  (:import java.io.PushbackReader))

(defn alias-info [full-ast]
  (-> full-ast first :alias-info))

(defn ns-from-string [ns-string]
  (second (read-ns-decl (PushbackReader. (java.io.StringReader. ns-string)))))

(defn list-project-clj-files [dir]
  (find-clojure-sources-in-dir (java.io.File. dir)))

(defn node-at-loc? [loc-line loc-column node]
  (let [env (:env node)]
    (and (>= loc-line (:line env))
         (<= loc-line (:end-line env))
         (>= loc-column (:column env))
         (<= loc-column (:end-column env)))))

(defn top-level-form-index
  [line column ns-ast]
  (->> ns-ast
       (map-indexed #(vector %1 (->> %2
                                     nodes
                                     (some (partial node-at-loc? line column)))))
       (filter #(second %))
       ffirst))

(defn throw-unless-clj-file [file]
  (when (and file (or (.endsWith file ".cljs") (.endsWith file ".cljx")))
    (throw (IllegalArgumentException.
            "Refactor nrepl doesn't work on cljs or cljx files!"))))
