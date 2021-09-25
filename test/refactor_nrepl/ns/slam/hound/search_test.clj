(ns refactor-nrepl.ns.slam.hound.search-test
  (:require
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.ns.slam.hound.search :as sut]))

(def acceptable-error-messages
  #{"com/github/luben/zstd/ZstdInputStream"
    "org/brotli/dec/BrotliInputStream"
    "org/apache/tools/ant/Task"
    "com/sun/jdi/request/EventRequest"})

(def non-initializable-classes
  '#{org.mozilla.javascript.SecureCaller})

(defn resolve-class [sym]
  (try
    (Class/forName (str sym)
                   false
                   (-> (Thread/currentThread) .getContextClassLoader))
    (catch NoClassDefFoundError e
      ;; there are only 4 in ~7922 classes that cause NoClassDefFoundError,
      ;; see `#'acceptable-error-messages`.
      ;; They don't have to do with classpath parsing so there's nothing to be fixed.
      (is (contains? acceptable-error-messages (.getMessage e))
          (-> e (.getMessage)))
      e)
    (catch UnsupportedClassVersionError e
      e)))

(defn result-can-be-ignored? [v]
  (or
   (instance? NoClassDefFoundError v)
   (instance? UnsupportedClassVersionError v)
   (contains? non-initializable-classes v)))

(defn ok []
  (is (< 3000 (count @sut/available-classes))
      "There are plenty of completions offered / these's a test corpus")
  (is (< 3000 (count @sut/available-classes-by-last-segment)))

  (doseq [x @sut/available-classes
          :let [v (resolve-class x)]]
    (when-not (result-can-be-ignored? v)
      (is (class? v)
          (pr-str x))))

  (doseq [[suffix classes] @sut/available-classes-by-last-segment]
    (is (seq classes))
    (doseq [c classes
            :let [v (resolve-class c)]]
      (when-not (result-can-be-ignored? v)
        (is (class? v)
            (pr-str c)))

      (is (-> c str (.endsWith (str suffix))))))

  (is (= '[clojure.lang.ExceptionInfo]
         (get @sut/available-classes-by-last-segment 'ExceptionInfo))))

(deftest works
  (ok)
  (sut/reset)
  (ok))
