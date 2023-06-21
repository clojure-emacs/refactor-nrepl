(ns refactor-nrepl.ns.suggest-libspecs
  "Beta middleware, meant only for internal development. Subject to change."
  (:refer-clojure :exclude [alias])
  (:require
   [refactor-nrepl.core :as core]
   [refactor-nrepl.ns.libspecs :as libspecs]
   [refactor-nrepl.ns.ns-parser :as ns-parser]
   [refactor-nrepl.util.meta :as meta]))

(defn vconj [coll x]
  (if coll
    (conj coll x)
    [x]))

(def parse-preferred-aliases
  (memoize (fn parse-preferred-aliases* [preferred-aliases]
             (let [m (volatile! {})]
               (doseq [[prefix ns-name _only-keyword only] (mapv (partial mapv (comp symbol
                                                                                     name)) ;; `name` for Clojure <= 1.9 compat
                                                                 preferred-aliases)
                       :let [files (ns-parser/ns-sym->ns-filenames ns-name)
                             only (or only [:clj :cljs])
                             only (if (coll? only)
                                    (vec only)
                                    (vector only))
                             clj?  (some #{"clj" :clj 'clj} only)
                             cljs? (some #{"cljs" :cljs 'cljs} only)
                             used-from (cond
                                         (and clj? cljs?) [:clj :cljs]
                                         clj?             [:clj]
                                         cljs?            [:cljs])
                             ns-name (cond-> ns-name
                                       files     (vary-meta assoc :files files)
                                       used-from (vary-meta assoc :used-from used-from))]]
                 (when clj?
                   (vswap! m update-in [:clj prefix] vconj ns-name))
                 (when cljs?
                   (vswap! m update-in [:cljs prefix] vconj ns-name)))
               @m))))

(comment
  (parse-preferred-aliases [["set" "clojure.set"]
                            ["cljs" "cljs" :only "cljs"]
                            ["clj" "clj" :only "clj"]]))

(defn build-reader-conditional [left-branch left-libspec _other-branch right-libspec as-alias]
  (let [l-clj? (= left-branch :clj)
        clj-clause (if l-clj?
                     left-libspec
                     right-libspec)
        cljs-clause (if l-clj?
                      right-libspec
                      left-libspec)]
    (format "#?(:clj [%s :as %s]\n      :cljs [%s :as %s])"
            clj-clause
            as-alias
            cljs-clause
            as-alias)))

;; XXX cache
(defn filenames->extensions [filenames]
  (into []
        (comp (map (fn [s]
                     (re-find #".clj[cs]?$" s)))
              (distinct))
        filenames))

(defn files->platform [filenames]
  (let [extensions (filenames->extensions filenames)
        cljc? (some #{".cljc"} extensions)
        clj? (some #{".clj"} extensions)
        cljs? (some #{".cljs"} extensions)]
    (cond
      (and cljc?
           (not cljs?)) ;; a ns backed by .cljc and .cljs files most likely is only a cljs-oriented ns in practice, since the .cljc file only intends to define macros (this is the case for clojurescript's clojure.test)
      :cljc

      (and clj? cljs?)
      :cljc,

      clj?
      :clj,

      cljs?
      :cljs

      (and cljc? cljs?) ;; See comment above
      :cljs)))

(defn valid-cljc-files?
  "Does the set of `filenames` denote a namespace that can be required from both Clojure and ClojureScript?"
  [filenames]
  {:post [(instance? Boolean %)]}
  (= :cljc (files->platform filenames)))

(defn build-reader-conditionals-from [ns-syms as-alias]
  (let [{:keys [clj cljs cljc]} (->> ns-syms
                                     (group-by (fn [ns-sym]
                                                 (some-> ns-sym meta :files files->platform))))]
    (into []
          (comp cat
                (distinct))
          [(for [clj clj
                 cljs cljs]
             (build-reader-conditional :clj clj
                                       :cljs cljs
                                       as-alias))
           (for [clj clj
                 cljs cljc]
             (build-reader-conditional :clj clj
                                       :cljs cljs
                                       as-alias))
           (for [clj cljc
                 cljs cljs]
             (build-reader-conditional :clj clj
                                       :cljs cljs
                                       as-alias))

           (for [i cljc
                 j cljc
                 :when (not= i j)
                 :let [[left right] (if (->> i meta :used-from (some #{:clj}))
                                      [i j]
                                      [j i])]]
             (build-reader-conditional :clj left
                                       :cljs right
                                       as-alias))])))

(defn build-partial-reader-conditional [ns-sym as-alias i-cljc?]
  (let [files (-> ns-sym meta :files (doto (assert "No :files found")))
        platform (if (some #{".clj"} (filenames->extensions files))
                   :clj
                   :cljs)
        other-platform (if (= :clj platform)
                         :cljs
                         :clj)]
    (if i-cljc?
      (build-reader-conditional platform ns-sym
                                other-platform ""
                                as-alias)
      (format "#?(%s [%s :as %s])"
              platform
              ns-sym
              as-alias))))

(defn add-cljc-key [{:keys [clj cljs] :as m} as-alias]
  (let [left (get clj as-alias)
        right (get cljs as-alias)
        v (some->> [left right]
                   (into []
                         (comp cat
                               (filter (fn [candidate]
                                         (some-> candidate meta :files valid-cljc-files?)))))
                   (not-empty)
                   (meta/distinct libspecs/merge-libspecs-meta))]
    (cond-> m
      v (assoc-in [:cljc as-alias] v))))

(defn vec-distinct-into [x y]
  (->> [x y]
       (reduce into [])
       (meta/distinct libspecs/merge-libspecs-meta)))

(defn maybe-add-reader-conditionals-from-preferences
  [b-cljc? aliases as-alias parsed-preferred-aliases existing from-preferred]
  ;; IF b-cljc? AND `existing` only exists in one branch (:clj, :cljs), AND from-preferred applies to the other branch,
  ;; include both.
  (let [reader-conditionals
        (when b-cljc?
          (let [branches-left (->> [:clj :cljs]
                                   (filter (fn [k]
                                             (some (set (get-in aliases [k as-alias]))
                                                   existing))))
                branches-right (->> [:clj :cljs]
                                    (filter (fn [k]
                                              (some (set (get-in parsed-preferred-aliases [k as-alias]))
                                                    from-preferred))))
                other-branch? (and (-> branches-left count #{1})
                                   (seq branches-right)
                                   (some (complement (set branches-left))
                                         branches-right))
                left-branch (first branches-left)
                other-branch (when other-branch?
                               (case left-branch
                                 :clj :cljs
                                 :cljs :clj))]
            (when other-branch
              (into []
                    (comp (filter (fn [libspec]
                                    ((set (get-in parsed-preferred-aliases [other-branch as-alias]))
                                     libspec)))
                          (map (fn [right-libspec]
                                 (keep (fn [left-libspec]
                                         (when-not (= left-libspec right-libspec)
                                           (build-reader-conditional left-branch left-libspec
                                                                     other-branch right-libspec
                                                                     as-alias)))
                                       existing)))
                          cat)
                    from-preferred))))]
    (cond-> existing ;; The baseline approach is to disregard `from-preferred` (i.e. any data from `map-from-preferred`) on conflict.
      reader-conditionals (into reader-conditionals))))

(defn suggest-libspecs-response
  "Implements https://github.com/clojure-emacs/refactor-nrepl/issues/384."
  [{:keys [lib-prefix ;; "set", representing that the user typed `set/`
           ^String buffer-language-context ;; Represents the file extension (or more rarely, major-mode, repl type, etc).
           ;;                                  *  One of: cljs, clj, cljc
           ^String input-language-context ;; Represents the context of what the user is typing.
           ;;                                  * Outside a reader conditional, its value should be identical to that of buffer-language-context
           ;;                                  * Inside a reader conditional, its value should be typically one of clj, cljs (not cljc)
           ;;                                  * For the edge case of typing #?(io/), you can default to buffer-language-context
           preferred-aliases ;; the entire value of cljr-magic-require-namespaces. See also https://github.com/clojure-emacs/clj-refactor.el/issues/530 for an intended future feature.
           suggest ;; the value of cljr-suggest-namespace-aliases
           ignore-errors
           namespace-aliases-fn]
    :or   {ignore-errors true
           suggest       true}}]
  {:pre [lib-prefix
         buffer-language-context
         input-language-context
         preferred-aliases
         (not (string? suggest)) ;; should be boolean or nil
         (not (string? ignore-errors))]} ;; should be boolean or nil
  (let [namespace-aliases-fn (or namespace-aliases-fn
                                 libspecs/namespace-aliases)
        ^String input-language-context (or (not-empty input-language-context)
                                           buffer-language-context)
        as-alias (symbol lib-prefix)
        b-cljc? (.equals buffer-language-context "cljc")
        i-clj?  (.equals input-language-context "clj")
        i-cljs? (.equals input-language-context "cljs")
        i-cljc? (.equals input-language-context "cljc")
        aliases (namespace-aliases-fn ignore-errors
                                      (core/source-dirs-on-classpath)
                                      suggest)
        aliases (cond-> aliases
                  b-cljc? (add-cljc-key as-alias))
        ks (into []
                 (comp (filter identity)
                       (distinct))
                 [(when i-clj?
                    :clj)
                  (when i-cljs?
                    :cljs)
                  (when i-cljc?
                    :clj)
                  (when i-cljc?
                    :cljs)
                  (when b-cljc?
                    :cljc)])
        maps (keep (fn [k]
                     (get aliases k))
                   ks)
        parsed-preferred-aliases (parse-preferred-aliases preferred-aliases)
        map-from-preferred (->> ks
                                (map (fn [k]
                                       (get parsed-preferred-aliases k)))
                                (apply merge-with vec-distinct-into))
        merged (apply merge-with vec-distinct-into maps)
        final (merge-with (fn [existing from-preferred]
                            (maybe-add-reader-conditionals-from-preferences b-cljc? aliases as-alias parsed-preferred-aliases existing from-preferred))
                          merged
                          map-from-preferred)
        candidates (get final as-alias)
        candidates (into candidates
                         (when b-cljc? ;; XXX maybe I need other conditions, refining b-cljc?
                           (build-reader-conditionals-from candidates as-alias)))
        candidates (if-not b-cljc? ;; XXX maybe I need other conditions, refining b-cljc?
                     candidates
                     (or (not-empty (into []
                                          (remove (fn invalid? [x]
                                                    (and (not (string? x)) ;; reader conditionals are ok
                                                         ;; single platform suggestions are not valid in a reader conditional context
                                                         ;; XXX but they are on i-clj, i-cljs
                                                         (contains? #{:clj :cljs}
                                                                    (some-> x meta :files files->platform)))))
                                          candidates))
                         candidates))]
    (into []
          (keep (fn [candidate]
                  (cond
                    (string? candidate)
                    candidate ;; was already processed as a reader conditional string, leave as-is

                    (and b-cljc?
                         (false? (some-> candidate meta :files valid-cljc-files?)))
                    (build-partial-reader-conditional candidate as-alias i-cljc?)

                    :else ;; it's data, format it:
                    (format "[%s :as %s]"
                            candidate
                            as-alias))))
          candidates)))

(comment
  (suggest-libspecs-response {:lib-prefix "set"
                              :buffer-language-context "clj"
                              :input-language-context "clj"
                              :preferred-aliases []})

  (suggest-libspecs-response {:lib-prefix "test"
                              :buffer-language-context "cljc"
                              :input-language-context "clj"
                              :preferred-aliases []})

  (suggest-libspecs-response {:lib-prefix "test"
                              :buffer-language-context "cljc"
                              :input-language-context "cljs"
                              :preferred-aliases []}))
