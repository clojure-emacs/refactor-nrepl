(ns refactor-nrepl.descriptors
  "What the ops take and return, in the form nREPL's `describe` op reports.

  The README has the long version. This is the part a client, or a person
  poking at a running server, can look up without it. It's also what the test
  suite holds the ops to: an op that replies with a key that isn't listed
  under its `:returns` fails the tests.")

(def find-symbol-params
  {"file" "The absolute path to the file containing the symbol to look up."
   "ns" "The namespace the symbol is defined in."
   "name" "The name of the symbol."
   "line" "The line the symbol occurs on, counting from 1."
   "column" "The column the symbol occurs at, counting from 1."})

(def ops
  "The descriptors, by the bare name of the op."
  {"artifact-list"
   {:doc "List the artifacts available on Clojars, and those under org.clojure and com.cognitect on Maven Central."
    :optional {"force" "Refresh the cached list first when \"true\"."}
    :returns {"artifacts" "A list of artifact names."}}

   "artifact-versions"
   {:doc "List the versions of an artifact, most relevant first."
    :requires {"artifact" "The full name of the artifact, e.g. org.clojure/clojure."}
    :returns {"versions" "A sorted list of versions."}}

   "clean-ns"
   {:doc "Clean up the ns form of a file: sort it, prune what's unused, and favor prefix notation if so configured."
    :requires {"path" "The absolute path to the file."}
    :optional {"relative-path" "The path relative to the project root, used when `path` doesn't exist."
               "always-return-ns-form" "Return the ns form even if cleaning it up changed nothing."}
    :returns {"ns" "The cleaned up ns form, or nil if there was nothing to do."}}

   "cljr-suggest-libspecs"
   {:doc "Suggest libspecs for a namespace alias, based on the aliases in use in the project."
    :requires {"lib-prefix" "The alias to look up, e.g. \"set\" for set/."
               "buffer-language-context" "The language of the file: \"clj\", \"cljs\" or \"cljc\"."
               "input-language-context" "The language at point, which differs from the file's inside a reader conditional."
               "preferred-aliases" "The preferred alias mappings, as a string of edn."}
    :returns {"suggestions" "A list of libspecs, e.g. [\"[clojure.set :as set]\"]."}}

   "extract-definition"
   {:doc "Find the definition of a symbol and its occurrences, for inlining it."
    :requires find-symbol-params
    :optional {"ignore-errors" "Carry on past files that can't be analyzed when \"true\"."}
    :returns {"definition" "A string of edn with the `:definition` and its `:occurrences`."}}

   "find-symbol"
   {:doc "Find the occurrences of a symbol in the project."
    :requires find-symbol-params
    :optional {"ignore-errors" "Carry on past files that can't be analyzed when \"true\"."
               "serialization-format" "\"edn\" (the default) or \"bencode\"."}
    :returns {"occurrence" "An occurrence. Each one is sent in a message of its own."
              "count" "The number of occurrences, sent in the final message."
              "skipped" "The files that couldn't be analyzed and were skipped, sent in the final message."}}

   "find-used-locals"
   {:doc "Find the locals used by the s-expression at a position."
    :requires {"file" "The path of the file."
               "line" "The line of the s-expression, counting from 1."
               "column" "The column of the s-expression, counting from 1."}
    :returns {"used-locals" "A list of the locals, in order of occurrence."}}

   "find-used-publics"
   {:doc "Find the occurrences in a file of the symbols another namespace defines."
    :requires {"file" "The absolute path to the file being analyzed."
               "used-ns" "The namespace that defines the symbols to look for."}
    :optional {"serialization-format" "\"edn\" (the default) or \"bencode\"."}
    :returns {"used-publics" "A list of occurrences."}}

   "hotload-dependency"
   {:doc "Load a dependency, and its transitive dependencies, into the running REPL."
    :requires {"coordinates" "A Leiningen-style dependency vector, or a deps.edn map literal."}
    :returns {"dependency" "The coordinates that were loaded."}}

   "namespace-aliases"
   {:doc "List the namespace aliases in use in the project."
    :optional {"suggest" "Also suggest aliases for namespaces that haven't been aliased yet."
               "serialization-format" "\"edn\" (the default) or \"bencode\"."}
    :returns {"namespace-aliases" "A map of aliases to namespaces, for each of :clj and :cljs."}}

   "rename-file-or-dir"
   {:doc "Rename a file or a directory, and update the namespaces that refer to what was moved."
    :requires {"old-path" "The absolute path to the file or directory."
               "new-path" "The absolute path to move it to."}
    :optional {"ignore-errors" "Carry on past files that can't be analyzed when \"true\"."}
    :returns {"touched" "A list of the files that were affected."}}

   "resolve-missing"
   {:doc "Suggest namespaces and classes that would make an unresolved symbol available."
    :requires {"symbol" "The symbol to look up on the classpath."}
    :optional {"ns" "The current namespace."}
    :returns {"candidates" "A list of candidates, as a string of edn."}}

   "stubs-for-interface"
   {:doc "List the methods of an interface or a protocol, for stubbing an implementation."
    :requires {"interface" "The fully qualified name of the interface or protocol."}
    :optional {"serialization-format" "\"edn\" (the default) or \"bencode\"."}
    :returns {"functions" "A list of the methods, each with a :name and a :parameter-list."}}

   "version"
   {:doc "Return the version of refactor-nrepl."
    :returns {"version" "The version."}}

   "warm-ast-cache"
   {:doc "Build and cache the ASTs of all the Clojure files in the project."
    :optional {"serialization-format" "\"edn\" (the default) or \"bencode\"."}
    :returns {"ast-statuses" "A list alternating namespaces and how building their ASTs went: \"OK\", or the error."}}})
