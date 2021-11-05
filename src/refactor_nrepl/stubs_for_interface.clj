(ns refactor-nrepl.stubs-for-interface
  (:require
   [clojure.reflect :as reflect]
   [clojure.string :as str]
   [refactor-nrepl.core :as core]))

(defn- format-type-hint
  [t]
  (let [type-hint (if (= (core/prefix t) "java.lang")
                    (str "^" (core/suffix t))
                    (str "^" t))]
    ;; varargs
    (if (.endsWith type-hint "<>")
      (.replace type-hint "<>" "...")
      type-hint)))

(defn- format-parameter-name
  ([index num-params]
   (str "arg" (if (= num-params 1) "" index)))
  ([index _num-params _protocol]
   (if (= index 0) "this" (str "arg" index))))

(defn- format-parameter-list
  [parameter-types]
  (if (seq parameter-types)
    (let [params (map-indexed
                  (fn [i t]
                    (str (format-type-hint t) " "
                         (format-parameter-name i (count parameter-types))))
                  parameter-types)]
      (str "[" (str/join " " params) "]"))
    "[]"))

(defn- declared-by?
  "Does THING declare F or is it inherited?"
  [thing f]
  (= (core/suffix thing)
     (core/suffix (:declaring-class f))))

(defn- prune-reflect-result
  [reflect-result thing]
  (->> reflect-result
       :members
       (filter (partial declared-by? thing))
       (map #(hash-map :name (str (:name %))
                       :parameter-list (format-parameter-list
                                        (:parameter-types %))))))

(defn extract-fn-info
  "Extracts information about the functions defined in the protocol.

  The result should match the return value of clojure.reflect/reflect"
  [info]
  (for [arglist (:arglists info)
        :let [params (map-indexed (fn [i _] (format-parameter-name
                                             i (count arglist) :protocol))
                                  arglist)]]
    {:name (str (:name info))
     :parameter-list (str "[" (str/join " " params) "]")}))

(defn stubs-for-interface
  "Get functions defined by protocol / interface."
  [{:keys [interface]}]
  (if-let [v (-> interface symbol resolve)]
    (if (instance? java.lang.Class v)
      (prune-reflect-result (reflect/reflect v) v)
      (mapcat extract-fn-info (-> v deref :sigs vals)))
    (throw (IllegalArgumentException.
            (str "Can't find interface " interface)))))
