(ns refactor-nrepl.find.util
  (:require [clojure.java.io :as io]
            [refactor-nrepl.core :as core]))

(defn spurious?
  "True if the occurrence doesn't exist at the given coordinates."
  ([{:keys [file ^long line-beg ^long col-beg ^long col-end name ^String match] :as occ}]
   ;; coordinates are wrong for def forms, they match the beginning of
   ;; the form not the first mention of the symbol being defined
   (when-not (and match (.startsWith match "(def"))
     (let [thing-in-file (->> file
                              io/reader
                              line-seq
                              (drop (dec line-beg))
                              first
                              (drop (dec col-beg))
                              (take (- col-end col-beg))
                              (apply str))]
       (not= (core/suffix thing-in-file)
             (core/suffix name)))))
  ([file occ]
   (spurious? (assoc occ :file file))))
