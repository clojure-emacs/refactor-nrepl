(ns refactor-nrepl.externs
  (:import java.net.URL
           org.projectodd.shimdandy.ClojureRuntimeShim
           java.net.URLClassLoader
           java.util.ArrayList))

(def rt (atom nil))

(defn create-runtime []
  (let [urls [(URL. "file:libs/shimdandy-impl-1.1.0.jar")
              (URL. "file:libs/clojure-1.6.0.jar")
              (URL. "file:libs/pomegranate-0.3.0.jar")]
        cl (URLClassLoader. (into-array URL urls))]
    (reset! rt (ClojureRuntimeShim/newRuntime cl "refactor-nrepl"))))

(defn add-dependencies [dep repos]
  (when-not @rt
    (create-runtime))
  (.require @rt (into-array String ["cemerick.pomegranate"]))
  (let [deps (.invoke @rt "cemerick.pomegranate.aether/resolve-dependencies"
                      :coordinates [dep]
                      :repositories repos)
        classloaders (.invoke @rt "cemerick.pomegranate/classloader-hierarchy")
        cl (last (filter #(.invoke @rt "cemerick.pomegranate/modifiable-classloader?" %) classloaders))]
    (doseq [artifact-file
            (.invoke @rt "cemerick.pomegranate.aether/dependency-files" deps)]
      (.invoke @rt "cemerick.pomegranate/add-classpath" artifact-file cl))
    deps))
