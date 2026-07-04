(ns pvcli.command
  "Shared command-tree dispatch for the service modules (pv, ts, grout).

   A command tree is a map of command-name → node, where a node is either:
     leaf    {:help-summary <str> :spec <map> :handler <fn>}
     parent  {:help-summary <str> :sub {name node ...}}

   A handler is a 1-arg fn taking a flattened options map:
     {:cfg <full config map> :args [positional ...] <parsed-option> ...}

   Options come from the leaf's babashka.cli :spec; positionals are the
   leftover non-option tokens. HTTP failures thrown by a handler propagate
   to pvcli.main, which prints them to stderr and exits non-zero."
  (:require [babashka.cli :as cli]
            [clojure.string :as str]
            [pvcli.output :as output]))

(defn find-leaf
  "Walk `tree` following `args`. Returns [leaf-node path-taken] or nil.
   A leaf is any node with a :handler. Stops at the first leaf whose next
   arg is not one of its subcommands, so trailing positionals stay in args."
  [tree args]
  (loop [m {:sub tree}
         r args
         acc []]
    (cond
      (empty? r)
      (when (:handler m) [m acc])

      (and (:handler m) (not (contains? (:sub m) (first r))))
      [m acc]

      (contains? (:sub m) (first r))
      (recur (get-in m [:sub (first r)]) (rest r) (conj acc (first r)))

      :else nil)))

(defn- print-leaf-help [service path leaf]
  (println (str service " " (str/join " " path) " — " (:help-summary leaf)))
  (when (seq (:spec leaf))
    (println)
    (println "Options:")
    (println (cli/format-opts {:spec (:spec leaf)}))))

(defn- flatten-parsed
  "babashka.cli nests options under :opts and puts positionals under :cmds
   or :args depending on position; flatten into one map the handler can
   destructure, with :cfg injected and positionals under :args."
  [parsed cfg]
  (merge (:opts parsed)
         {:cfg cfg
          :args (vec (concat (:cmds parsed) (:args parsed)))}))

(defn dispatch
  "Generic service dispatch. `service` is the display name (\"pv\"), `help`
   the full service help string, `tree` the command map. `cfg` is the
   resolved config, `mode` is :json or :human, and `args` is the argv after
   the service name."
  [service help tree cfg mode args]
  (cond
    (or (empty? args)
        (and (= 1 (count args))
             (contains? #{"--help" "-h" "help"} (first args))))
    (do (println help) (System/exit 0))

    (and (= 2 (count args))
         (contains? #{"--help" "-h" "help"} (second args)))
    (let [path-args (take 1 args)
          result (find-leaf tree path-args)]
      (if result
        (print-leaf-help service path-args (first result))
        (do (binding [*out* *err*]
              (println (str "Unknown " service " subcommand: " (first args))))
            (println help)
            (System/exit 2))))

    :else
    (let [result (find-leaf tree args)]
      (if-not result
        (do (binding [*out* *err*]
              (println (str "Unknown or incomplete " service " command: "
                            (str/join " " args)))
              (println (str "Run 'pvcli " service " --help' for the list.")))
            (System/exit 2))
        (let [[leaf path-taken] result
              path-len (count path-taken)
              leaf-args (vec (drop path-len args))]
          (if (some #(contains? #{"--help" "-h"} %) leaf-args)
            (print-leaf-help service path-taken leaf)
            (let [parsed (try
                           (cli/parse-args leaf-args
                                           {:spec (:spec leaf)
                                            :error-fn (fn [m]
                                                        (binding [*out* *err*]
                                                          (println "Error:" (:msg m)))
                                                        (System/exit 2))})
                           (catch clojure.lang.ExceptionInfo e
                             (binding [*out* *err*]
                               (println "Error:" (ex-message e)))
                             (System/exit 2)))]
              (output/emit-and-print! ((:handler leaf) (flatten-parsed parsed cfg))
                                      mode))))))))
