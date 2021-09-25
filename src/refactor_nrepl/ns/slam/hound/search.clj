;;;; Copied from slamhound 1.5.5
;;;; Copyright © 2011-2012 Phil Hagelberg and contributors
;;;; Distributed under the Eclipse Public License, the same as Clojure.
(ns refactor-nrepl.ns.slam.hound.search
  "Search the classpath for vars and classes."
  (:require
   [clojure.java.io :refer [file]]
   [clojure.string :as string]
   [refactor-nrepl.util :as util])
  (:import
   (java.io File FilenameFilter)
   (java.util StringTokenizer)
   (java.util.jar JarEntry JarFile)
   (java.util.regex Pattern)))

(defn jar? [^File f]
  (and (.isFile f) (.endsWith (.getName f) ".jar")))

(defn class-file? [^String path]
  (.endsWith path ".class"))

(defn clojure-fn-file? [^String file]
  ;; originally this logic was: (re-find #"\$.*__\d+\.class" f)
  ;; however that doesn't cover e.g. "clojure/spec/alpha$double_in.class"
  ;; so we mimic the logic that e.g. Compliment has:
  (or (.contains file "__")
      (.contains file "$")))

(defn clojure-ns-file? [^String path]
  (.endsWith path "__init.class"))

(def jar-filter
  (proxy [FilenameFilter] []
    (accept [d n] (jar? (file n)))))

(defn expand-wildcard
  "Expands a wildcard path entry to its matching .jar files (JDK 1.6+).
  If not expanding, returns the path entry as a single-element vector."
  [^String path]
  (let [f (File. path)]
    (if (= (.getName f) "*")
      (.. f getParentFile (list jar-filter))
      [f])))

(def resource-separator
  "Please do not use File/separator see e.g. https://git.io/Jzig3"
  "/")

(defn class-name
  [^String path]
  (-> path
      (.replace ".class" "")
      (.replace resource-separator ".")))

(defmulti path-class-files
  (fn [^File f _loc]
    (cond
      (.isDirectory f) :dir
      (jar? f) :jar
      (class-file? (.getName f)) :class)))

(defmethod path-class-files :default [& _] [])

(defmethod path-class-files :jar
  ;; Build class info for all jar entry class files.
  [^File f ^File loc]
  (let [_lp (.getPath loc)]
    (try
      (into ()
            (comp
             (map #(.getName ^JarEntry %))
             (filter class-file?)
             (remove clojure-fn-file?)
             (map class-name))
            (enumeration-seq (.entries (JarFile. f))))
      (catch Exception e
        (util/maybe-log-exception e)
        ;; fail gracefully if jar is unreadable:
        []))))

(defmethod path-class-files :dir
  ;; Dispatch directories and files (excluding jars) recursively.
  [^File d ^File loc]
  (let [fs (.listFiles d (reify FilenameFilter
                           (accept [_ dir name]
                             (-> name file jar? not))))]
    (into () (mapcat #(path-class-files % loc)) fs)))

(defmethod path-class-files :class
  ;; Build class info using file path relative to parent classpath entry
  ;; location. Make sure it decends; a class can't be on classpath directly.
  [^File f ^File loc]
  (let [fp (str f)
        lp (str loc)]
    (if (re-find (re-pattern (Pattern/quote (str "^" loc))) fp) ; must be descendent of loc
      (let [fpr (.substring fp (inc (count lp)))]
        [(class-name fpr)])
      [])))

(defn path-entries-seq
  "Split a string on the 'path separator', i.e. ':'. Used for splitting multiple
  classpath entries."
  [path-str]
  (-> path-str
      (StringTokenizer. File/pathSeparator)
      enumeration-seq))

(defn- get-available-classes []
  (into ()
        (comp (mapcat expand-wildcard)
              (mapcat (fn [file]
                        (path-class-files file file)))
              (remove clojure-fn-file?)
              (distinct)
              (map symbol))
        ;; We use `(System/getProperty "java.class.path")` (at least for the time being) because
        ;; This code was originally written to handle that string, not a list
        ;; (this code was broken for a while as `orchard.java.classpath` was being incompatibly used instead)
        (path-entries-seq (System/getProperty "java.class.path"))))

(def available-classes
  (delay (get-available-classes)))

(defn- get-available-classes-by-last-segment []
  (group-by #(symbol (peek (string/split (str %) #"\."))) @available-classes))

(def available-classes-by-last-segment
  (delay (get-available-classes-by-last-segment)))

(defn reset
  "Reset the cache of classes"
  []
  (alter-var-root #'available-classes (constantly (delay (get-available-classes))))
  (alter-var-root #'available-classes-by-last-segment (constantly (delay (get-available-classes-by-last-segment)))))
