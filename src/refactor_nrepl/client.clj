(ns refactor-nrepl.client
  (:require [clojure.tools.nrepl :as nrepl]
            [clojure.string :as str]))

(def ^:private nrepl-port (-> ".nrepl-port" slurp Integer/parseInt))

(def ^:private transp (atom nil))

(defn- nrepl-message [tr payload]
  (nrepl/message (nrepl/client tr 1000) payload))

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

(defn connect
  "Connects to an nrepl server. The client won't be functional if the nrepl server does not have refactor-nrepl middleware added to it.

  Expected inputs:
  - host where the nrepl server runs, defaults to localhost
  - port on which the nrepl server listens,
    defaults to the contents of port in .nrepl-port file"
  [& {:keys [host port] :or {port nrepl-port
                             host "localhost"}}]
  (nrepl/connect :port port :host host))

(defn remove-debug-invocations
  "Removes debug function invocations. In reality it could remove any function invocations.

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
    (->> ns-string
         str/split-lines
         (remove-invocations invocations)
         (remove (partial = "$remove$"))
         (str/join "\n")
         (spit file))))
