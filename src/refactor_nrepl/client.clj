(ns refactor-nrepl.client
  (:require [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.tools.nrepl :as nrepl])
  (:import java.io.File))

(def ^:private nrepl-port
  (when (.exists (java.io.File. ".nrepl-port"))
    (-> ".nrepl-port" slurp Integer/parseInt)))

(def ^:private transp (atom nil))

(def ^:private esc \u001b)

(def ^:private red "[31m")

(def ^:private yellow "[33m")

(def ^:private reset "[0m")

(defn- nrepl-message
  ([timeout tr payload]
   (nrepl/message (nrepl/client tr timeout) payload))
  ([tr payload]
   (nrepl-message 5000 tr payload)))

(defn- remove-invocation [invocation lines]
  (for [line lines
        :let [i (.indexOf lines line)
              line-index (-> invocation first dec)
              end-line-index (-> invocation second dec)
              from (if (= i line-index)
                     (dec (nth invocation 2))
                     0)
              to (if (= i end-line-index)
                   (dec (nth invocation 3))
                   (-> line count))]]
    (if (and (>= i line-index) (<= i end-line-index))
      (-> line
          (str/replace (.substring line from to) "")
          (#(if (str/blank? %) "$remove$" %)))
      line)))

;; multiple invocations to remove in the same line not supported yet
(defn- remove-invocations [invocations lines]
  (loop [lines lines
         invocations invocations]
    (if (empty? invocations)
      lines
      (recur (remove-invocation (first invocations) lines)
             (rest invocations)))))

(defn- colorise-found [sym full-hit]
  (let [no-ns-sym (-> sym (str/split #"/") last)
        replacement (str esc red no-ns-sym esc reset)]
    (str/replace full-hit (re-pattern no-ns-sym) replacement)))

(defn- prettify-found-symbol-result [[line _ _ _ sym path match]]
  (->> match
       (colorise-found sym)
       (str esc yellow path esc reset "[" line "]" ": ")))

(defn- replace-in-line [name new-name occ-line-index col indexed-line]
  (let [line (val indexed-line)]
    (if (= (key indexed-line) occ-line-index)
      (let [col (if col (dec col) 0)
            [line-first line-rest] (->> line
                                        (split-at col)
                                        (map #(apply str %)))]
        (str line-first (str/replace-first line-rest name new-name)))
      line)))

(defn- rename-symbol-occurrence!
  [name new-name [line end-line col end-col sym path _]]
  (let [occ-line-index (dec line)]
    (->> path
         slurp
         str/split-lines
         (interleave (range))
         (#(apply sorted-map %))
         (map (partial replace-in-line name new-name occ-line-index col))
         (str/join "\n")
         (#(str % "\n"))
         (spit path))))

(defn connect
  "Connects to an nrepl server. The client won't be functional if the nrepl
  server does not have refactor-nrepl middleware added to it.

  Expected inputs:
  - host where the nrepl server runs, defaults to localhost
  - port on which the nrepl server listens,
  defaults to the contents of port in .nrepl-port file"
  [& {:keys [host port] :or {port nrepl-port
                             host "localhost"}}]
  (nrepl/connect :port port :host host))

(defn- act-on-occurrences [action & {:keys [transport ns name clj-dir loc-line loc-column file]}]
  (let [tr (or transport @transp (reset! transp (connect)))
        req {:op :refactor
             :refactor-fn "find-symbol"
             :ns ns
             :clj-dir (or clj-dir ".")
             :loc-line loc-line
             :loc-column loc-column
             :name name}
        found-symbols (->> req
                           (#(if file (assoc % :file file) %))
                           (nrepl-message 60000 tr)
                           (map (juxt :occurrence :syms-count)))]
    (println (format "found %d occurrences of %s%s"
                     (->> found-symbols
                          (map second)
                          (remove nil?)
                          first)
                     (if ns (str ns "/") "") name))
    (->> found-symbols
         (map first)
         (remove nil?)
         (map action)
         doall)))


(defn find-usages
  "Finds and lists symbols (defs, defns) in the project: both where they are defined and their occurrences.

  Expected input:
  - ns namespace of the symbol to find
  - name of the symbol to find as string
  - clj-dir director to search clj files in, defaults to `.`
  - transport optional, however if you don't provide your own repl
  transport the client will create and store its own. therefore it is
  preferred that you create, store and manage your own transport by calling
  the connect function in this namespace so the client does not get stateful"
  [& {:keys [transport ns name clj-dir file loc-line loc-column]}]
  {:pre [(or (and ns name) (and file loc-line loc-column))]}
  (act-on-occurrences prettify-found-symbol-result :transport transport
                      :ns ns :name name :clj-dir clj-dir :file file :loc-line loc-line :loc-column loc-column))

(defn rename-symbol
  "Renames symbols (defs and defns) in the project's given dir.

  Expected input:
  - ns namespace of the symbol to find
  - name of the symbol to find as string
  - new-name to rename to
  - clj-dir director to search clj files in, defaults to `.`
  - transport optional, however if you don't provide your own repl
  transport the client will create and store its own. therefore it is
  preferred that you create, store and manage your own transport by calling
  the connect function in this namespace so the client does not get stateful"
  [& {:keys [transport ns name new-name clj-dir]}]
  {:pre [ns name new-name]}
  (act-on-occurrences (partial rename-symbol-occurrence! name new-name)
                      :transport transport :ns ns :name name :clj-dir clj-dir))

(defn find-referred
  "Finds referred symbol in namespace.

  Temporary as only needed for temporary performance tweak for remove requires in clj-refactor.el. Will be removed when refactor-nrepl based clean-ns is implemented"
  [& {:keys [transport file referred]}]
  {:pre [file referred]}
  (let [tr (or transport @transp (reset! transp (connect)))
        ns-string (slurp file)
        result (->> {:op :refactor
                     :ns-string ns-string
                     :referred referred
                     :refactor-fn "find-referred"}
                    (nrepl-message tr)
                    first
                    :value
                    (#(if (coll? %) (not-empty %) %)))]
    (println (format "Referred %s is %s in %s" referred (if result "found" "not found") file))
    result))

(defn var-info [& {:keys [transport file name]}]
  {:pre [file name]}
  (let [tr (or transport @transp (reset! transp (connect)))
        ns-string (slurp file)]
    (-> (nrepl-message tr {:op :refactor
                           :ns-string ns-string
                           :refactor-fn "var-info"
                           :name name})
        first
        :var-info)))

(defn remove-debug-invocations
  "Removes debug function invocations. In reality it could remove any function
  invocations.

  Expected input:
  - debug-fns see documenation on find (debug) function invocations for
  expected format. defaults to \"println,pr,prn\"
  - file to refactor, provide path as you would provide for slurp
  - transport optional, however if you don't provide your own repl
  transport the client will create and store its own. therefore it is
  preferred that you create, store and manage your own transport by calling
  the connect function in this namespace so the client does not get stateful"
  [& {:keys [transport file debug-fns] :or {debug-fns "println,pr,prn"}}]
  {:pre [file]}
  (let [tr (or transport @transp (reset! transp (connect)))
        ns-string (slurp file)
        result (nrepl-message tr {:op :refactor
                                  :ns-string ns-string
                                  :refactor-fn "find-debug-fns"
                                  :debug-fns debug-fns})
        invocations (-> result first :value)]
    (println "found invocations: " invocations)
    (when-not (empty? invocations)
      (->> ns-string
           str/split-lines
           (remove-invocations invocations)
           (remove (partial = "$remove$"))
           (str/join "\n")
           (#(str % "\n"))
           (spit file)))))

(defn resolve-missing
  "Resolve a missing symbol to provide candidates for imports.

  Expected input:
  - symbol which is either a var or a class to resolve on the classpath
  - [transport] an optional transport used to communicate with the client"
  [& {transport :transport sym :symbol}]
  (let [tr (or transport @transp (reset! transp (connect)))
        candidates (-> (nrepl-message tr {:op :resolve-missing
                                          :symbol sym})
                       first
                       :candidates)]
    (when-not (str/blank? candidates)
      (->> (str/split candidates #" ")
           (map symbol)))))

(defn find-unbound
  "Finds unbound vars in the input form

  Expected input:
  - ns which is the ns to be analyzed for unbound vars
  - [transport] an optional transport used to communicate with the client"
  [& {:keys [transport ns]}]
  (let [tr (or transport @transp (reset! transp (connect)))
        response (nrepl-message tr {:op :find-unbound
                                    :ns ns})
        unbound (:unbound (first response))]
    (if-not (str/blank? unbound)
      (->> (str/split unbound #" ")
           (map symbol)
           set)
      #{})))
