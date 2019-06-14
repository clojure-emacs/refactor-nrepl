(ns refactor-nrepl.tramp)

(def ^:private tramp-params-regex
  #"^/([a-zA-Z0-9-]+):(?:([^/|: 	]+)@)?((?:[a-zA-Z0-9_.%-]+|\[(?:(?:(?:[a-zA-Z0-9]+)?:)+[a-zA-Z0-9.]+)?\])(?:#[0-9]+)?)?:(.*)$")

(defn remove-tramp-params
  "Remove the TRAMP params and hostname from the file path."
  [f]
  (or (last (first (re-seq tramp-params-regex f)))
      f))

(defn extract-tramp-params
  "Extract the TRAMP params and hostname from the file path."
  [f]
  (let [m (some->> (re-seq tramp-params-regex f)
                   first
                   rest
                   (interleave [:method :user :host :path])
                   (apply assoc {}))]
    (if (:path m)
      m
      {:path f})))

(defn with-tramp-params
  "Add the TRAMP params and hostname to the file path."
  [{:keys [method user host]} f]
  (if (and method host)
    (str "/" method ":" (when user (str user "@")) host ":" f)
    f))
