(ns refactor-nrepl.ns.class-search-test
  (:require
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [refactor-nrepl.ns.class-search :as sut]))

(def acceptable-error-messages
  #{"com/github/luben/zstd/ZstdInputStream"
    "org/brotli/dec/BrotliInputStream"
    "org/apache/tools/ant/Task"
    "org/apache/tools/ant/launch/AntMain"
    "com/sun/jdi/request/EventRequest"})

(def non-initializable-classes
  '#{org.mozilla.javascript.SecureCaller})

(defn handle [^Throwable e]
  (is (or
       ;; there are only ~5 in ~7922 classes that cause NoClassDefFoundError,
       ;; see `#'acceptable-error-messages`.
       ;; They don't have to do with classpath parsing so there's nothing to be fixed:
       (contains? acceptable-error-messages (.getMessage e))
       (some (fn [prefix]
               (-> e
                   .getMessage
                   (string/includes? prefix)))
             [;; Other internal classes introduced with JDK9:
              "org.graalvm"
              "sun."
              "jdk."
              ;; Odd stuff brought in by the `fs` dependency:
              "Implementing class"
              "org.apache.commons.compress.harmony.pack200.Segment can not implement"
              "javax/xml/bind/ModuleUtil (wrong name: META-INF/versions/9/javax/xml/bind/ModuleUtil)"])
       (do
         (.printStackTrace e)
         false))
      (-> e .getMessage pr-str))
  e)

(defn resolve-class [sym]
  (try
    (Class/forName (str sym)
                   false
                   (-> (Thread/currentThread) .getContextClassLoader))
    (catch NoClassDefFoundError e
      (handle e))
    (catch IncompatibleClassChangeError e
      (handle e))
    (catch ClassNotFoundException e
      (handle e))
    (catch UnsupportedClassVersionError e
      e)))

(defn result-can-be-ignored? [v]
  (or
   (instance? NoClassDefFoundError v)
   (instance? ClassNotFoundException v)
   (instance? UnsupportedClassVersionError v)
   (instance? IncompatibleClassChangeError v)
   (contains? non-initializable-classes v)))

(defn ok []
  (is (< 3000 (count @sut/available-classes))
      "There are plenty of completions offered / these's a test corpus")

  (is (some #{'java.nio.channels.FileChannel} @sut/available-classes))
  (is (some #{'java.io.File} @sut/available-classes))
  (is (some #{'java.lang.Thread} @sut/available-classes))

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
