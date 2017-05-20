(ns refactor-nrepl.ns.pprint
  (:require [cljfmt.core :as fmt]
            [clojure
             [pprint :refer [pprint]]
             [string :as str]]
            [refactor-nrepl.core :as core :refer [prefix-form?]])

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

(defn- pprint-gen-class-form
  [[_ & elems]]
  (if (empty? elems)
    (println "(:gen-class)")
    (println "(:gen-class"))
  (dorun
   (map-indexed
    (fn [idx [key val]]
      (if (= idx (dec (count (partition 2 elems))))
        (printf "%s %s)\n" key val)
        (println key val)))
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

(defn pprint-meta
  [m]
  (if-let [shorthand-meta-coll (::core/shorthand-meta-coll m)]
    (doseq [shorthand-meta shorthand-meta-coll]
      (print (str "^" shorthand-meta " ")))
    (printf (.replaceAll (str "^" (into (sorted-map) m) "\n")
                         ", " "\n"))))

(defn pprint-ns
  [[_ name & more :as ns-form]]
  (let [docstring? (when (string? (first more)) (first more))
        attrs? (when (map? (second more)) (second more))
        forms (cond (and docstring? attrs?) (nthrest more 2)
                    (not (or docstring? attrs?)) more
                    :else (rest more))
        ns-meta (meta ns-form)]
    (-> (with-out-str
          (printf "(ns ")
          (when (seq ns-meta) (pprint-meta ns-meta))
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
                                 (form-is? form :gen-class) (pprint-gen-class-form form)
                                 (form-is? form :import) (pprint-import-form form)
                                 :else (pprint form)))))
                (cond (form-is? form :require) (pprint-require-form form)
                      (form-is? form :gen-class) (pprint-gen-class-form form)
                      (form-is? form :import) (pprint-import-form form)
                      :else (pprint form))))
            forms)))
        (.replaceAll "\r" "")
        fmt/reformat-string
        (.replaceAll (Pattern/quote "#? @") "#?@"))))
