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

(defn- nrepl-message
  ([timeout tr payload]
   (nrepl/message (nrepl/client tr timeout) payload))
  ([tr payload]
   (nrepl-message 5000 tr payload)))

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

(defn- act-on-occurrences [action & {:keys [transport ns name dir line column file ignore-errors]}]
  (let [tr (or transport @transp (reset! transp (connect)))
        req {:op :find-symbol
             :ns ns
             :dir (or dir ".")
             :line line
             :column column
             :name name
             :ignore-errors ignore-errors}
        found-symbols (->> req
                           (#(if file (assoc % :file file) %))
                           (nrepl-message 60000 tr)
                           (map (juxt :occurrence :count :error)))]
    (if-let [error (some last found-symbols)]
      (throw (IllegalStateException. (str error))))
    (->> found-symbols
         (map first)
         (remove nil?)
         (map edn/read-string)
         (map action)
         doall)))

(defn- prettify-found-symbol-result [{:keys [line-beg name file match]}]
  (->> match
       (str file " " "[" line-beg "]" ": ")))

(defn find-usages
  "Finds and lists symbols (defs, defns) in the project: both where they are defined and their occurrences.

  Expected input:
  - ns namespace of the symbol to find
  - name of the symbol to find as string
  - dir director to search clj files in, defaults to `.`
  - transport optional, however if you don't provide your own repl
  - line optional, line of the symbol to find in the source file
  - column optional, column of the symbol to find in the source file
  - file to refactor, provide path as you would provide for slurp
  transport the client will create and store its own. therefore it is
  preferred that you create, store and manage your own transport by calling
  the connect function in this namespace so the client does not get stateful"
  [& {:keys [transport ns name dir file line column]}]
  {:pre [(or (and ns name) (and file line column))]}
  (act-on-occurrences prettify-found-symbol-result :transport transport
                      :ns ns :name name :dir dir :file file :line line :column column :ignore-errors "true"))

(defn resolve-missing
  "Resolve a missing symbol to provide candidates for imports.

  Expected input:
  - symbol which is either a var or a class to resolve on the classpath
  - [transport] an optional transport used to communicate with the client"
  [& {transport :transport sym :symbol}]
  (-> transport
      (or @transp (reset! transp (connect)))
      (nrepl-message {:op :resolve-missing :symbol sym})
      first
      :candidates
      edn/read-string))

(defn find-unbound
  "Finds unbound vars in the input form

  Expected input:
  - ns which is the ns to be analyzed for unbound vars
  - [transport] an optional transport used to communicate with the client"
  [& {:keys [transport file line column]}]
  (let [tr (or transport @transp (reset! transp (connect)))
        response (nrepl-message tr {:op :find-used-locals
                                    :file file
                                    :line line
                                    :column column})
        unbound (:used-locals (first response))
        error (:error (first response))]
    (if error
      (do (println "something bad happened: " error) (first response))
      (map symbol unbound))))

(defn version
  "Returns the version of the middleware"
  [& {:keys [transport]}]
  (let [tr (or transport @transp (reset! transp (connect)))
        response (nrepl-message tr {:op :version})]
    (:version (first response))))
