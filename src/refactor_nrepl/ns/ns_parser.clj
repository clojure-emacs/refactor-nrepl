(ns refactor-nrepl.ns.ns-parser
  (:require [clojure.java.io :as io]
            [instaparse.core :refer [parse parser]]
            [refactor-nrepl.ns.helpers :refer [get-ns-component]]))

(defn- parse-form
  "Form is either (:import..) (:use ..) or (:require ..)"
  [form]
  (let [ns-parser (parser
                   (io/resource "refactor_nrepl/ns/require-or-use-or-import.bnf")
                   :auto-whitespace :comma)]
    (parse ns-parser (str form))))

(defn- add-prefix-to-libspec
  [prefix libspec]
  (if (sequential? libspec)
    (let [suffix (second libspec)]
      (assoc libspec 0 :libspec-with-opts
             1 (str prefix "." suffix)))
    (str prefix "." libspec)))

(defn- use-to-refer-all
  [libspec]
  (if (nil? (:refer libspec))
    (update-in libspec [:refer] (constantly :all))
    libspec))

(defmulti parse-libspec first)

(defn- extract-referred [libspec]
  (let [refer (some->> libspec (drop-while #(not= % ":refer")) second)]
    (if (sequential? refer)
      (map symbol (rest refer))
      (when refer
        :all))))

(defn- extract-rename-spec [libspec]
  (some->> libspec
           (drop-while #(not= % ":rename"))
           second
           rest
           (map symbol)
           (apply hash-map)))

(defmethod parse-libspec :libspec-with-opts
  [[_ ns & libspec]]
  {:ns (symbol ns)
   :as (some->> libspec (drop-while #(not= % ":as")) second symbol)
   :refer (extract-referred libspec)
   :rename (extract-rename-spec libspec)
   :only (some->> libspec (drop-while #(not= % ":only")) second rest)
   :flags (some->> libspec (filter #{":reload" ":reload-all" ":verbose"}))})

(defmethod parse-libspec :libspec-no-opts
  [[_ ns]]
  {:ns (symbol ns)})

(defmethod parse-libspec :prefix-libspec
  [[_ prefix & libspecs]]
  (->> libspecs
       (map (partial add-prefix-to-libspec prefix))
       (map parse-libspec)))

(defn- extract-libspecs
  [form]
  (flatten
   (let [parse-tree (rest (second (parse-form form)))]
     (for [libspec parse-tree]
       (parse-libspec libspec)))))

(defmulti parse-import first)

(defmethod parse-import :class
  [import]
  (second import))

(defn- add-package-prefix-to-class
  [prefix [_ class-name :as class]]
  (assoc class 1 (str prefix "." class-name)))

(defmethod parse-import :classes-with-prefix
  [[_ prefix & classes]]
  (->> classes
       (map (partial add-package-prefix-to-class prefix))
       (map parse-import)))

(defn- extract-imports
  [form]
  (let [parse-tree (rest (second (parse-form form)))]
    (for [import parse-tree]
      (parse-import import))))

(defn- extract-used [use-form]
  (some->>  use-form
            extract-libspecs
            (map use-to-refer-all)))

(defn- extract-requires [require-form]
  (some-> require-form
          extract-libspecs))

(defn get-libspecs [ns-form]
  (concat
   (extract-used (get-ns-component ns-form :use))
   (extract-requires (get-ns-component ns-form :require))))

(defn get-imports [ns-form]
  (some-> (get-ns-component ns-form :import) extract-imports
          flatten))
