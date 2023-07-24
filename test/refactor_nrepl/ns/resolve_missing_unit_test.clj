(ns refactor-nrepl.ns.resolve-missing-unit-test
  {:clj-kondo/config {:linters {:unused-namespace    {:level :off}
                                :refer               {:level :off}
                                :unused-referred-var {:level :off}}}}
  (:require
   [refactor-nrepl.ns.resolve-missing :as sut]
   [clojure.test :refer [are deftest is testing]]
   ;; Supports this deftest - note the :refer and :rename
   [clojure.spec.alpha :refer [int-in inst-in] :rename {inst-in renamed-inst-in}]
   [clojure.string :as string]))

(defn remove-cljs
  "Removes cljs members because their presence depends on flaky conditions
  (piggieback, whether other tests have been run, etc)."
  [coll]
  (into []
        (remove (fn [x]
                  (-> x :name (string/includes? "cljs"))))
        coll))

(deftest resolve-missing-unit-test
  (let [this-ns         (namespace ::_)
        non-existing-ns (-> (java.util.UUID/randomUUID) str)]

    (are [input expected] (testing input
                            (is (= expected
                                   (-> input
                                       (assoc :refactor-nrepl.internal/force-jvm? true)
                                       sut/resolve-missing
                                       read-string
                                       remove-cljs)))
                            true)
      {:symbol "Thread"}                      '[{:name java.lang.Thread, :type :class}]
      ;; :already-interned returns true for Thread (Clojure interns this class by default):
      {:symbol "Thread" :ns this-ns}          '[{:name java.lang.Thread, :type :class, :already-interned true}]
      {:symbol "Thread" :ns non-existing-ns}  '[{:name java.lang.Thread, :type :class}]
      {:symbol "File"}                        '[{:name java.io.File, :type :class}]
      ;; :already-interned returns false for File:
      {:symbol "File" :ns this-ns}            '[{:name java.io.File, :type :class, :already-interned false}]
      {:symbol "File" :ns non-existing-ns}    '[{:name java.io.File, :type :class}]
      {:symbol "+"}                           '[{:name clojure.spec.alpha, :type :ns} {:name clojure.core, :type :ns}]
      ;; :already-interned returns false for spec, true for code:
      {:symbol "+" :ns this-ns}               '[{:name clojure.spec.alpha, :type :ns, :already-interned false}
                                                {:name clojure.core, :type :ns, :already-interned true}]
      {:symbol "+" :ns non-existing-ns}       '[{:name clojure.spec.alpha, :type :ns} {:name clojure.core, :type :ns}]
      {:symbol "int-in"}                      '[{:name clojure.spec.alpha, :type :ns}]
      ;; :already-interned returns true for refers:
      {:symbol "int-in" :ns this-ns}          '[{:name clojure.spec.alpha, :type :ns, :already-interned true}]
      {:symbol "int-in" :ns non-existing-ns}  '[{:name clojure.spec.alpha, :type :ns}]
      {:symbol "inst-in"}                     '[{:name clojure.spec.alpha, :type :ns}]
      ;; :already-interned returns true for refer + rename:
      {:symbol "inst-in" :ns this-ns}         '[{:name clojure.spec.alpha, :type :ns, :already-interned true}]
      {:symbol "inst-in" :ns non-existing-ns} '[{:name clojure.spec.alpha, :type :ns}])))
