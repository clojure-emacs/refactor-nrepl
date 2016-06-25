;;;; Copied from slamhound 1.5.5
;;;; Copyright © 2011-2012 Phil Hagelberg and contributors
;;;; Distributed under the Eclipse Public License, the same as Clojure.
(ns refactor-nrepl.ns.slam.hound.search
  "Search the classpath for vars and classes."
  (:require [clojure.java.classpath :as cp]
            [clojure.java.io :refer [file]]
            [clojure.string :as string])
  (:import
   [java.io File FilenameFilter]
   [java.util.jar JarFile JarEntry]
   java.util.regex.Pattern
   java.util.StringTokenizer))

;;; Mostly taken from leiningen.util.ns and swank.util.class-browse.

;; TODO: replace with bultitude? but that doesn't do classes

;;; Clojure namespaces

(defn jar? [^File f]
  (and (.isFile f) (.endsWith (.getName f) ".jar")))

(defn class-file? [^String path]
  (.endsWith path ".class"))

(defn clojure-fn-file? [f]
  (re-find #"\$.*__\d+\.class" f))

(defn clojure-ns-file? [^String path]
  (.endsWith path "__init.class"))

;;; Java classes

;; could probably be simplified

(def jar-filter
  (proxy [FilenameFilter] []
    (accept [d n] (jar? (file n)))))

(defn expand-wildcard
  "Expands a wildcard path entry to its matching .jar files (JDK 1.6+).
  If not expanding, returns the path entry as a single-element vector."
  [#^String path]
  (let [f (File. path)]
    (if (= (.getName f) "*")
      (.. f getParentFile (list jar-filter))
      [f])))

(defn class-or-ns-name
  "Returns the Java class or Clojure namespace name for a class relative path."
  [^String path]
  (-> (if (clojure-ns-file? path)
        (-> path (.replace "__init.class" "") (.replace "_" "-"))
        (.replace path ".class" ""))
      (.replace File/separator ".")))

(def path-class-files nil)
(defmulti path-class-files
  "Returns a list of classes found on the specified path location
  (jar or directory), each comprised of a map with the following keys:
    :name  Java class or Clojure namespace name
    :loc   Classpath entry (directory or jar) on which the class is located
    :file  Path of the class file, relative to :loc"
  (fn [#^File f _]
    (cond (.isDirectory f)           :dir
          (jar? f)        :jar
          (class-file? (.getName f)) :class)))

(defmethod path-class-files :default
  [& _] [])

(defmethod path-class-files :jar
  ;; Build class info for all jar entry class files.
  [#^File f #^File loc]
  (let [lp (.getPath loc)]
    (try
      (map class-or-ns-name
           (filter class-file?
                   (map #(.getName #^JarEntry %)
                        (enumeration-seq (.entries (JarFile. f))))))
      (catch Exception e []))))          ; fail gracefully if jar is unreadable

(defmethod path-class-files :dir
  ;; Dispatch directories and files (excluding jars) recursively.
  [#^File d #^File loc]
  (let [fs (.listFiles d (proxy [FilenameFilter] []
                           (accept [d n] (not (jar? (file n))))))]
    (reduce concat (for [f fs] (path-class-files f loc)))))

(defmethod path-class-files :class
  ;; Build class info using file path relative to parent classpath entry
  ;; location. Make sure it decends; a class can't be on classpath directly.
  [#^File f #^File loc]
  (let [fp (str f), lp (str loc)
        loc-pattern (re-pattern (Pattern/quote (str "^" loc)))]
    (if (re-find loc-pattern fp)                 ; must be descendent of loc
      (let [fpr (.substring fp (inc (count lp)))]
        [(class-or-ns-name fpr)])
      [])))

(defn scan-paths
  "Takes one or more classpath strings, scans each classpath entry location, and
  returns a list of all class file paths found, each relative to its parent
  directory or jar on the classpath."
  ([cp]
     (if cp
       (let [entries (enumeration-seq
                      (StringTokenizer. cp File/pathSeparator))
             locs (mapcat expand-wildcard entries)]
         (mapcat #(path-class-files % %) locs))
       ())))

(defn- get-available-classes
  []
  (->> (mapcat scan-paths (concat (map #(System/getProperty %) ["sun.boot.class.path"
                                                                "java.ext.dirs"
                                                                "java.class.path"])
                                  (map #(.getName %) (cp/classpath-jarfiles))))
       (remove clojure-fn-file?)
       (map symbol)))

(def available-classes
  (get-available-classes))

(defn- get-available-classes-by-last-segment
  []
  (delay
   (group-by #(symbol (peek (string/split (str %) #"\."))) available-classes)))

(def available-classes-by-last-segment
  (get-available-classes-by-last-segment))

(defn reset
  "Reset the cache of classes"
  []
  (alter-var-root #'available-classes (constantly (get-available-classes)))
  (alter-var-root #'available-classes-by-last-segment (constantly (get-available-classes-by-last-segment))))
