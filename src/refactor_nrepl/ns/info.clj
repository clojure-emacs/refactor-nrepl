(ns refactor-nrepl.ns.info
  "Copied over from cider 0.8.2 cider.nrepl.middleware.info namespace"
  (:require [clojure.repl :as repl]
            [refactor-nrepl.ns.java :as java]))

(defn maybe-protocol
  [info]
  (if-let [prot-meta (meta (:protocol info))]
    (merge info {:file (:file prot-meta)
                 :line (:line prot-meta)})
    info))

(def var-meta-whitelist
  [:ns :name :doc :file :arglists :forms :macro :protocol :line :column :static :added :deprecated :resource])

(defn- map-seq [x]
  (if (seq x)
    x
    nil))

(defn var-meta
  [v]
  (-> v meta maybe-protocol (select-keys var-meta-whitelist) map-seq))

(defn ns-meta
  [ns]
  (merge
   (meta ns)
   {:ns ns
    :file (-> (ns-publics ns)
              first
              second
              var-meta
              :file)
    :line 1}))

(defn resolve-var
  [ns sym]
  (if-let [ns (find-ns ns)]
    (try (ns-resolve ns sym)
         ;; Impl might try to resolve it as a class, which may fail
         (catch ClassNotFoundException _
           nil)
         ;; TODO: Preserve and display the exception info
         (catch Exception _
           nil))))

(defn resolve-aliases
  [ns]
  (if-let [ns (find-ns ns)]
    (ns-aliases ns)))

;; This reproduces the behavior of the canonical `clojure.repl/doc` using its
;; internals, but returns the metadata rather than just printing. Oddly, the
;; only place in the Clojure API that special form metadata is available *as
;; data* is a private function. Lame. Just call through the var.
(defn resolve-special
  "Return info for the symbol if it's a special form, or nil otherwise. Adds
  `:url` unless that value is explicitly set to `nil` -- the same behavior
  used by `clojure.repl/doc`."
  [sym]
  (try
    (let [sym (get '{& fn, catch try, finally try} sym sym)
          v   (meta (ns-resolve (find-ns 'clojure.core) sym))]
      (when-let [m (cond (special-symbol? sym) (#'repl/special-doc sym)
                         (:special-form v) v)]
        (assoc m
          :url (if (contains? m :url)
                 (when (:url m)
                   (str "http://clojure.org/" (:url m)))
                 (str "http://clojure.org/special_forms#" (:name m))))))
    (catch Exception _)))

(defn info-clj
  [ns sym]
  (cond
   ;; it's a special (special-symbol? or :special-form)
   (resolve-special sym) (resolve-special sym)
   ;; it's a var
   (var-meta (resolve-var ns sym)) (var-meta (resolve-var ns sym))
   ;; sym is an alias for another ns
   (get (resolve-aliases ns) sym) (ns-meta (get (resolve-aliases ns) sym))
   ;; it's simply a full ns
   (find-ns sym) (ns-meta (find-ns sym))
   ;; it's a Java class/member symbol...or nil
   :else (java/resolve-symbol ns sym)))
