(ns refactor-nrepl.ns.class-search
  "Search the classpath for classes.

  Formerly known as `refactor-nrepl.ns.slam.hound.search`."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (java.io File)
   (java.nio.file Files)
   (java.util.concurrent.locks ReentrantLock)
   (java.util.function Function Predicate)
   (java.util.jar JarEntry JarFile)
   (java.util.stream Collectors)))

(def ^:private simple-cache (atom {}))

(defn- classpath-strings []
  (into [] (keep #(System/getProperty %))
        ["sun.boot.class.path" "java.ext.dirs" "java.class.path"]))

(let [lock (ReentrantLock.)]
  (defn- recompute-if-classpath-changed [value-fn]
    (.lock lock)
    (try (let [cache @simple-cache
               cp-hash (reduce hash-combine 0 (classpath-strings))
               same-cp? (= cp-hash (:classpath-hash cache))
               cached-value (:files-on-classpath cache)]
           (if (and (some? cached-value) same-cp?)
             cached-value
             (let [value (value-fn)]
               (reset! simple-cache {:classpath-hash cp-hash
                                     :files-on-classpath value})
               value)))
         (finally (.unlock lock)))))

(defn- classpath
  "Returns a sequence of File objects of the elements on the classpath."
  []
  (mapcat #(.split ^String % File/pathSeparator) (classpath-strings)))

(defn- file-seq-nonr
  "A tree seq on java.io.Files, doesn't resolve symlinked directories to avoid
  infinite sequence resulting from recursive symlinked directories."
  [dir]
  (tree-seq
   (fn [^File f] (and (.isDirectory f) (not (Files/isSymbolicLink (.toPath f)))))
   (fn [^File d] (seq (.listFiles d)))
   dir))

(defn- list-files
  "Given a path (either a jar file, directory with classes or directory with
  paths) returns all files under that path."
  [^String path, scan-jars?]
  (cond (.endsWith path "/*")
        (for [^File jar (.listFiles (File. path))
              :when (.endsWith ^String (.getName jar) ".jar")
              file (list-files (.getPath jar) scan-jars?)]
          file)

        (.endsWith path ".jar")
        (if scan-jars?
          (try (-> (.stream (JarFile. path))
                   (.filter (reify Predicate
                              (test [_ entry]
                                (not (.isDirectory ^JarEntry entry)))))
                   (.map (reify Function
                           (apply [_ entry]
                             (.getName ^JarEntry entry))))
                   (.collect (Collectors/toList)))
               (catch Exception _))
          ())

        (= path "") ()

        (.exists (File. path))
        (let [root (File. path)
              root-path (.toPath root)]
          (for [^File file (file-seq-nonr root)
                :when (not (.isDirectory file))]
            (let [filename (str (.relativize root-path (.toPath file)))]
              (cond-> filename
                ;; Replace Windows backslashes with slashes.
                (not= File/separator "/") (.replace File/separator "/")
                (.startsWith filename "/") (.substring filename 1)))))))

(defmacro list-jdk9-base-classfiles
  "Because on JDK9+ the classfiles are stored not in rt.jar on classpath, but in
  modules, we have to do extra work to extract them."
  []
  (when (try (ns-resolve *ns* 'java.lang.module.ModuleFinder) (catch Exception _))
    `(-> (.findAll (java.lang.module.ModuleFinder/ofSystem))
         (.stream)
         (.flatMap (reify Function
                     (apply [_ mref#]
                       (.list (.open ^java.lang.module.ModuleReference mref#)))))
         (.collect (Collectors/toList)))))

(defn- all-files-on-classpath*
  "Given a list of files on the classpath, returns the list of all files,
  including those located inside jar files."
  [classpath]
  (let [seen (java.util.HashMap.)
        seen? (fn [x] (.putIfAbsent seen x x))]
    (-> []
        (into (comp (map #(list-files % true)) cat (remove seen?)) classpath)
        (into (remove seen?) (list-jdk9-base-classfiles)))))

(defn- classes-on-classpath* [files]
  (let [filename->classname
        (fn [^String file]
          (when (.endsWith file ".class")
            (when-not (or (.contains file "__")
                          (.contains file "$")
                          (.equals file "module-info.class"))
              (let [c (-> (subs file 0 (- (.length file) 6)) ;; .class
                          ;; Resource separator is always / on all OSes.
                          (.replace "/" "."))]
                ;; https://github.com/alexander-yakushev/compliment/issues/105
                (when (io/resource (-> c (string/replace "." File/separator) (str ".class")))
                  c)))))]
    (into [] (comp (keep filename->classname) (distinct) (map symbol)) files)))

(defn available-classes []
  (classes-on-classpath* (all-files-on-classpath* (classpath))))

(defn- get-available-classes-by-last-segment []
  (group-by #(symbol (peek (string/split (str %) #"\."))) (available-classes)))

(defn available-classes-by-last-segment []
  (recompute-if-classpath-changed #(get-available-classes-by-last-segment)))
