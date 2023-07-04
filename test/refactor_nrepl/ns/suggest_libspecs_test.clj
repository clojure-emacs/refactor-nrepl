(ns refactor-nrepl.ns.suggest-libspecs-test
  (:require
   [clojure.test :refer [are deftest is testing]]
   [clojure.walk :as walk]
   [refactor-nrepl.ns.libspecs]
   [refactor-nrepl.ns.ns-parser :as ns-parser]
   [refactor-nrepl.ns.suggest-libspecs :as sut]))

(defn add-file-meta [libspecs]
  (let [libspecs (->> libspecs
                      (walk/postwalk (fn [x]
                                       (cond->> x
                                         (vector? x) (mapv (fn [s]
                                                             (let [files (ns-parser/ns-sym->ns-filenames s)]
                                                               (cond-> s
                                                                 files (vary-meta assoc :files files)))))))))
        c-paths (keys (:clj libspecs))
        s-paths (keys (:cljs libspecs))
        libspecs (reduce (fn [libspecs k]
                           (update-in libspecs [:clj k] (fn [candidates]
                                                          (mapv (fn [candidate]
                                                                  (vary-meta candidate assoc :used-from [:clj]))
                                                                candidates))))
                         libspecs
                         c-paths)]
    (reduce (fn [libspecs k]
              (update-in libspecs [:cljs k] (fn [candidates]
                                              (mapv (fn [candidate]
                                                      (vary-meta candidate assoc :used-from [:cljs]))
                                                    candidates))))
            libspecs
            s-paths)))

(deftest suggest-libspecs-response
  (reset! @#'refactor-nrepl.ns.libspecs/cache {})
  (are [lib-prefix buffer-lc input-lc preferred-aliases project-libspecs expected]
       (testing [lib-prefix buffer-lc input-lc preferred-aliases project-libspecs]
         (is (= expected
                (sut/suggest-libspecs-response {:lib-prefix lib-prefix
                                                :buffer-language-context buffer-lc
                                                :input-language-context input-lc
                                                :preferred-aliases preferred-aliases
                                                :namespace-aliases-fn (when (seq project-libspecs)
                                                                       ;; if provided, replace `refactor-nrepl.ns.libspecs/namespace-aliases`
                                                                       ;; with a mocked value, for test simplicity.
                                                                       ;; NOTE: an alternative approach would be to always use the original fn,
                                                                       ;; but perform a 'nested select keys' on it.
                                                                       ;; That way, we make sure the values are realistic.
                                                                        (constantly (add-file-meta project-libspecs)))})))
         true)
    #_lib-prefix #_buffer-lc #_input-lc #_preferred-aliases                  #_project-libspecs              #_expected

    ;; Some basic examples. I chose 'donkey' as a unique alias. Note that `project-libspecs` is {}, so a real project analysis is performed
    ;; (Which is why I chose a unique ns segment)
    "donkey"     "clj"       "clj"      []                                   {}                              ["[donkey.jvm :as donkey]"]
    "donkey"     "cljs"      "cljs"     []                                   {}                              ["[donkeyscript :as donkey]"]
    "donkey"     "cljc"      "clj"      []                                   {}                              ["#?(:clj [donkey.jvm :as donkey])"]
    "donkey"     "cljc"      "cljs"     []                                   {}                              ["#?(:cljs [donkeyscript :as donkey])"]
    "donkey"     "cljc"      "cljc"     []                                   {}                              ["#?(:clj [donkey.jvm :as donkey]\n      :cljs [donkeyscript :as donkey])"]

    ;; A set of examples, similar to the previous set. However the result will always be `clojure.test`, because cljs.test is now less recommended/usual,
    ;; so we shouldn't suggest reader conditionals when something simpler will do:
    "test"       "clj"       "clj"      []                                   {}                              ["[clojure.test :as test]"]
    "test"       "cljs"      "cljs"     []                                   {}                              ["[clojure.test :as test]"]
    "test"       "cljc"      "clj"      []                                   {}                              ["[clojure.test :as test]"]
    "test"       "cljc"      "cljs"     []                                   {}                              ["[clojure.test :as test]"]
    "test"       "cljc"      "cljc"     []                                   {}                              ["[clojure.test :as test]"]

    ;; Example story 1:
    "set"        "cljc"      "cljc"     [["set" "clojure.set"]]              {}                              ["[clojure.set :as set]"]
    ;; Story 2 - preferred-aliases are disregarded if the libspecs found in the project differ:
    "set"        "cljc"      "cljc"     [["set" "clojure.set"]]              '{:clj  {set [something-else]}
                                                                               :cljs {set [something-else]}} ["[something-else :as set]"]
    ;; `preferred-aliases` are taken into account when they don't conflict with the `project-libspecs`:
    "set"        "cljc"      "cljc"     [["set" "clojure.set"]]              '{:clj  {x [y]}
                                                                               :cljs {x [y]}}                ["[clojure.set :as set]"]

    ;; Story 3:
    "set"        "cljc"      "cljc"     [["set" "clojure.set"]]              '{:clj  {set [something-else
                                                                                           clojure.set]}
                                                                               :cljs {set [something-else
                                                                                           clojure.set]}}    ["[something-else :as set]" "[clojure.set :as set]"]

    ;; Story 4:
    "io"         "clj"       "clj"     [["io" "clojure.java.io" :only :clj]] '{:cljs {io [something-else]}}  ["[clojure.java.io :as io]"]
    ;; Story 5:
    "io"         "clj"       "clj"     [["io" "clojure.java.io" :only :clj]] '{:clj  {io [something-else]}}  ["[something-else :as io]"]
    "io"         "clj"       "clj"     [["io" "something-else" :only :cljs]] {}                              ["[clojure.java.io :as io]"]
    #_lib-prefix #_buffer-lc #_input-lc #_preferred-aliases                  #_project-libspecs              #_expected
    ;; Story 6:
    "io"         "cljc"      "cljc"    [["io" "clojure.java.io" :only :clj]] '{:cljs {io [test-cljc-ns]}}     ["[test-cljc-ns :as io]"
                                                                                                               "#?(:clj [clojure.java.io :as io]\n      :cljs [test-cljc-ns :as io])"]
    "io"         "cljc"      "clj"     [["io" "clojure.java.io" :only :clj]] '{:cljs {io [test-cljc-ns]}}     ["[test-cljc-ns :as io]"
                                                                                                               "#?(:clj [clojure.java.io :as io]\n      :cljs [test-cljc-ns :as io])"]
    "io"         "cljc"      "cljs"    [["io" "clojure.java.io" :only :clj]] '{:cljs {io [test-cljc-ns]}}     ["[test-cljc-ns :as io]"]
    "io"         "cljc"      "clj"     [["io" "clojure.java.io" :only :clj]] '{:clj  {io [test-clj-ns]}}      ["#?(:clj [test-clj-ns :as io])"]
    ;; The difference here is that test-cljs-ns is backed by a .cljs extension and therefore is not a valid .cljc or :clj suggestion
    "io"         "cljc"      "cljc"    [["io" "clojure.java.io" :only :clj]] '{:cljs {io [test-cljs-ns]}}     ["#?(:clj [clojure.java.io :as io]\n      :cljs [test-cljs-ns :as io])"]
    ;; Returns an empty form for the :clj branch when there's no valid suggestion for it:
    "io"         "cljc"      "cljc"    []                                    '{:cljs {io [test-cljs-ns]}}     ["#?(:clj [ :as io]\n      :cljs [test-cljs-ns :as io])"]
    "io"         "cljc"      "cljc"    []                                    '{:cljs {io [test-cljc-ns]}}     ["[test-cljc-ns :as io]"]
    ;; https://github.com/clojure-emacs/refactor-nrepl/issues/384#issuecomment-1221622306 extra cases
    ;; discards user preference, and offers both cljc choices individually and as a reader conditional (only one reader conditional! switching its branches makes less sense)
    "io"         "cljc"      "cljc"    [["io" "clojure.java.io" :only :clj]] '{:clj  {io [test-cljc-ns]}
                                                                               :cljs {io [test-cljc-ns-2]}}   ["[test-cljc-ns :as io]"
                                                                                                               "[test-cljc-ns-2 :as io]"
                                                                                                               "#?(:clj [test-cljc-ns :as io]\n      :cljs [test-cljc-ns-2 :as io])"]
    "io"         "cljc"      "cljc"    []                                    '{:clj  {io [test-clj-ns]}
                                                                               :cljs {io [test-cljs-ns]}}     ["#?(:clj [test-clj-ns :as io]\n      :cljs [test-cljs-ns :as io])"]
    "io"         "cljc"      "cljc"    []                                    '{:cljs {io [test-cljs-ns
                                                                                          test-cljs-ns-2]}}   ["#?(:clj [ :as io]\n      :cljs [test-cljs-ns :as io])"
                                                                                                               "#?(:clj [ :as io]\n      :cljs [test-cljs-ns-2 :as io])"]
    "io"         "cljc"      "cljc"    []                                    '{:clj  {io [test-cljc-ns]}
                                                                               :cljs {io [test-cljs-ns]}}     ["[test-cljc-ns :as io]"
                                                                                                               "#?(:clj [test-cljc-ns :as io]\n      :cljs [test-cljs-ns :as io])"]

    "io"         "cljc"      "cljs"    []                                    '{:cljs {io [test-cljs-ns]}}     ["#?(:cljs [test-cljs-ns :as io])"]

    "io"         "cljc"      "cljc"    []                                    '{:clj  {io [test-cljc-ns]}
                                                                               :cljs {io [test-cljc-ns-2]}}   ["[test-cljc-ns :as io]"
                                                                                                               "[test-cljc-ns-2 :as io]"
                                                                                                               "#?(:clj [test-cljc-ns :as io]\n      :cljs [test-cljc-ns-2 :as io])"]
    "io"         "cljc"      "clj"     []                                    '{:clj  {io [test-cljc-ns]}
                                                                               :cljs {io [test-cljc-ns-2]}}   ["[test-cljc-ns :as io]"
                                                                                                               "[test-cljc-ns-2 :as io]" ;; questionable but OK. It could be removed but it's not impossible thet the user wants it
                                                                                                               "#?(:clj [test-cljc-ns :as io]\n      :cljs [test-cljc-ns-2 :as io])"]
    "io"         "cljc"      "cljs"    []                                    '{:clj  {io [test-cljc-ns]}
                                                                               :cljs {io [test-cljc-ns-2]}}   ["[test-cljc-ns-2 :as io]"
                                                                                                               "[test-cljc-ns :as io]" ;; questionable but OK (Note that the questionable choice at least does not appear first)
                                                                                                               "#?(:clj [test-cljc-ns :as io]\n      :cljs [test-cljc-ns-2 :as io])"]))
