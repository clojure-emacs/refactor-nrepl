(ns refactor-nrepl.ns.pprint
  (:require [cljfmt.core :as fmt]
            [clojure
             [pprint :refer [pprint]]
             [string :as str]]
            [refactor-nrepl
             [core :as core :refer [prefix-form?]]
             [util :as util :refer [replace-last]]])

  (:import java.util.regex.Pattern))

(defn- libspec-vectors-last [libspecs]
  (vec (concat (remove sequential? libspecs)
               (filter sequential? libspecs))))

(defn- pprint-prefix-form [[name & libspecs]]
  (printf "[%s" name)
  (let [ordered-libspecs (libspec-vectors-last libspecs)]
    (dorun
     (map-indexed (fn [^long idx libspec]
                    ;; insert newline after all non-libspec vectors
                    (when (and (vector? libspec)
                               (or (zero? idx)
                                   (symbol? (get ordered-libspecs (dec idx)))))
                      (println))
                    (if (= idx (dec (count ordered-libspecs)))
                      (printf "%s]\n" libspec)
                      (if (vector? libspec)
                        (println libspec)
                        (if (zero? idx)
                          (printf " %s " libspec)
                          (if (vector? (get ordered-libspecs (inc idx)))
                            (printf "%s" libspec)
                            (printf "%s " libspec))))))
                  ordered-libspecs))))

(defn pprint-require-form
  [[_ & libspecs]]
  (print "(:require ")
  (dorun
   (map-indexed
    (fn [idx libspec]
      (if (= idx (dec (count libspecs)))
        (printf "%s)\n" (str/trim-newline
                         (with-out-str (if (prefix-form? libspec)
                                         (pprint-prefix-form libspec)
                                         (pprint libspec)))))
        (if (prefix-form? libspec)
          (pprint-prefix-form libspec)
          (pprint libspec))))
    libspecs)))

(defn- form-is? [form type]
  (and (sequential? form)
       (= (first form) type)))

(defn pprint-meta
  "Given some metadata m, print the shorthand metadata first, and the
  longhand metadata second, trying to convert to shorthand notation if
  possible

  If newlines is true, it prints a newline after each piece of
  longhand metadata"
  [m & {:keys [newlines]
        :or {newlines false}}]
  (let [short? #(= (str %) "true")
        shorthand (sort (filter (fn [[k v]] (short? v)) m))
        longhand (remove (fn [[k v]] (short? v)) m)]
    (doseq [[k v] shorthand]
      (print "^" (str k) ""))
    (when-not (empty? longhand)
      (printf "^{")
      (doseq [[k v] longhand]
        (print k)
        (if newlines
          (print (with-out-str (pprint v)))
          (print (replace-last (with-out-str (pprint v)) #"\s" ""))))
      (if newlines
        (println "}")
        (print "}")))))

(defn- pprint-gen-class-form
  "Prints the gen class form and :methods metadata (if any)."
  [[_ & elems] metadata]
  (if (empty? elems)
    (println "(:gen-class)")
    (println "(:gen-class"))
  (dorun
   (map-indexed
    (fn [idx [key val]]
      (if (= key :methods)
        (do
          (print key "[")
          (doseq [method val]           ;val are all the methods
            (pprint-meta (filter (fn [[k v]]
                                   (contains? metadata k))
                                 (meta method)))
            (print method)
            (when-not (= method (last val))
              (println)))
          (print "]"))
        (print key val))
      (when (= idx (dec (count (partition 2 elems))))
        (print ")"))
      (println))
    (partition 2 elems))))

(defn- pprint-import-form
  [[_ & imports]]
  (printf "(:import ")
  (dorun
   (map-indexed
    (fn [idx import]
      (if (= idx (dec (count imports)))
        (printf "%s)\n" import)
        (println import)))
    imports)))

(defn pprint-ns
  [[_ name & more :as ns-form]]
  (let [docstring? (when (string? (first more)) (first more))
        attrs? (when (map? (second more)) (second more))
        forms (cond (and docstring? attrs?) (nthrest more 2)
                    (not (or docstring? attrs?)) more
                    :else (rest more))
        ns-meta (:top-level-meta (meta ns-form))]
    (-> (with-out-str
          (printf "(ns ")
          (when (seq ns-meta) (pprint-meta ns-meta :newlines true))
          (print name)
          (if (or docstring? attrs? forms)
            (println)
            (print ")"))
          (when docstring?
            (printf "\"%s\"" (str/escape docstring? {\" "\\\""}))
            (if (or (seq attrs?) (seq forms))
              (print "\n")
              (print ")")))
          (when attrs?
            (pprint attrs?)
            (when (empty? forms)
              (print ")")))
          (dorun
           (map-indexed
            (fn [idx form]
              (if (= idx (dec (count forms)))
                (printf "%s)\n"
                        (str/trim-newline
                         (with-out-str
                           (cond (form-is? form :require) (pprint-require-form form)
                                 (form-is? form :gen-class) (pprint-gen-class-form form (:gc-methods-meta (meta ns-form)))
                                 (form-is? form :import) (pprint-import-form form)
                                 :else (pprint form)))))
                (cond (form-is? form :require) (pprint-require-form form)
                      (form-is? form :gen-class) (pprint-gen-class-form form (:gc-methods-meta (meta ns-form)))
                      (form-is? form :import) (pprint-import-form form)
                      :else (pprint form))))
            forms)))
        (str/replace "\r" "")
        fmt/reformat-string
        (str/replace (Pattern/quote "#? @") "#?@"))))
