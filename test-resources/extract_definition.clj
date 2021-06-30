(ns extract-definition)

(defn- private-function-definition-with-docstring-and-meta
  "docstring"
  [foo]
  {:pre [(not (nil? foo))]}
  (do
    (+ 1 2) ; discard this value
    :return))

(defn- private-function-definition-with-docstring-no-meta
  "docstring"
  [foo]
  (do
    (+ 1 2) ; discard this value
    :value))

(defn- private-function-definition-witout-docstring-and-meta
  [foo]
  (do
    (+ 1 2) ; discard this value
    :val))

(def ^:private private-var-no-docstring :value)
(def ^:private private-var-no-docstring-no-value)
(def ^:private private-var-with-docstring "Docstring 1" :value)
(def var-with-docstring-and-value "Docstring 2" :value)

(private-function-definition-with-docstring-and-meta :foo)
(private-function-definition-with-docstring-no-meta :foo)
(private-function-definition-witout-docstring-and-meta :foo)

(do
  private-var-no-docstring
  private-var-no-docstring-no-value
  private-var-with-docstring
  var-with-docstring-and-value
  (let [let-bound (+ 1 2)
        let-bound-multi-line (+ 1 (* 3 2)
                                (- 77 32) ; eol comment
                                (/ 9 3))]
    (+ let-bound let-bound-multi-line)
    (if-let [if-let-bound (+ 11 17)]
      if-let-bound
      :bar)))

(defn public-function []
  :value)

(public-function)

(let [a 1]
  (inc a))

(let [a 1
      b (+ 1 a)]
  (+ a b))
