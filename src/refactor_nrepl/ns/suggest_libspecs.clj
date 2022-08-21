(ns refactor-nrepl.ns.suggest-libspecs
  "Beta middleware, meant only for internal development. Subject to change."
  (:require
   [refactor-nrepl.core :as core]
   [refactor-nrepl.ns.libspecs :as libspecs]))

(defn suggest-libspecs-response
  "Very basic POC for https://github.com/clojure-emacs/refactor-nrepl/issues/384.

  Only focuses on its API and giving some basic (but useful) results.

  The results are returned in no particular order."
  [{:keys [lib-prefix ;; "set", representing that the user typed `set/`
           ^String language-context ;; "clj" "cljs" or "cljc" representing the filename extension (or a user choice for edge cases e.g. it's a buffer/repl without a filename)
           preferred-aliases ;; the entire value of cljr-magic-require-namespaces. See also https://github.com/clojure-emacs/clj-refactor.el/issues/530 for an intended future feature.
           suggest ;; the value of cljr-suggest-namespace-aliases
           ignore-errors]
    :or   {ignore-errors true
           suggest       true}}]
  {:pre [lib-prefix
         language-context
         preferred-aliases
         (boolean? suggest)
         (boolean? ignore-errors)]}
  (let [alias (symbol lib-prefix)
        aliases (libspecs/namespace-aliases ignore-errors
                                            (core/source-dirs-on-classpath)
                                            suggest)
        ks      (cond-> #{}
                  (.equals language-context "clj")  (conj :clj)
                  (.equals language-context "cljs") (conj :cljs)
                  (.equals language-context "cljc") (conj :clj :cljs)
                  true                              vec)
        maps (map (fn [k]
                    (get aliases k))
                  ks)
        merged (apply merge-with (fn [x y]
                                   (vec (distinct (into x y))))
                      maps)
        candidates (get merged alias)]
    (->> candidates
         (mapv (fn [candidate]
                 (format "[%s :as %s]"
                         candidate
                         alias))))))

(comment
  (suggest-libspecs-response {:lib-prefix "add-on"
                              :language-context "clj"
                              :preferred-aliases []})

  (suggest-libspecs-response {:lib-prefix "s"
                              :language-context "cljc"
                              :preferred-aliases []}))
