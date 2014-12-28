(ns refactor-nrepl.ns.pprint
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [refactor-nrepl.ns.helpers :refer [prefix-form?]]))

(defn- pprint-prefix-form [[name & libspecs]]
  (printf "[%s" name)
  (dorun
   (map-indexed (fn [idx libspec]
                  (when (and (sequential? libspec)
                             (or (= idx 0)
                                 (= idx (count (take-while symbol? libspecs)))))
                    (println))
                  (if (= idx (dec (count libspecs)))
                    (printf "%s]\n" libspec)
                    (if (sequential? libspec)
                      (pprint libspec)
                      (if (= idx 0)
                        (printf " %s " libspec)
                        (if (sequential? (nth libspecs (inc idx)))
                          (printf "%s" libspec)
                          (printf "%s " libspec))))))
                libspecs)))

(defn pprint-require-form
  [[_ & libspecs]]
  (print "(:require ")
  (dorun
   (map-indexed
    (fn [idx libspec]
      (if (= idx (dec (count libspecs)))
        (printf "%s)\n" (str/trim-newline
                         (with-out-str (pprint libspec))))
        (if (prefix-form? libspec)
          (pprint-prefix-form libspec)
          (pprint libspec))))
    libspecs)))

(defn- form-is? [form type]
  (and (sequential? form)
       (= (first form) type)))

(defn- pprint-gen-class-form
  [[_ & elems]]
  (println "(:gen-class")
  (dorun
   (map-indexed
    (fn [idx [key val]]
      (if (= idx (dec (count (partition 2 elems))))
        (printf "%s %s)\n" key val)
        (println key val)))
    (partition 2 elems))))

(defn- pprint-import-form
  [[_ & imports]]
  (printf "(:import " )
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
                    :else (rest more))]
    (-> (with-out-str
          (printf "(ns %s\n" name)
          (when docstring? (printf "\"%s\"\n" docstring? ))
          (when attrs? (pprint attrs?))
          (when-not forms
            (print ")"))
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
        (.replaceAll "\r" ""))))
