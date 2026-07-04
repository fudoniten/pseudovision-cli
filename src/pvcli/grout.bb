(ns pvcli.grout
  "Grout subcommands.

   For v0.1, only `info` is wired up. The `intake` command (lifted from
   the original `grout-cli.bb` in the Grout repo, but updated for the
   multipart intake contract) lands in the next PR."
  (:require [clojure.string :as str]
            [pvcli.http :as http]
            [pvcli.output :as output]))

(def help
  (str/join "\n"
            ["grout — Grout subcommands (filler media store)"
             ""
             "Usage:"
             "  pvcli grout <command> [options] [args]"
             ""
             "Commands:"
             "  info                    Service info (build, version, route count)"
             "  media list [--channel=X] [--tag=T] List filler media (filter by channel/tags/duration)"
             "  media get <uuid>        Fetch one media item"
             "  media by-hash <sha256>  Look up a media item by content hash"
             "  intake <file>...        Upload+tag one or more files (multipart)"
             "  tags add <uuid> <tag>   Add a tag to an existing media item"
             ""
             "Run 'pvcli grout <command> --help' for command-specific options."
             ""]))

(defn- info
  "Probe the service. Returns a map with :service, :reachable, :version."
  [_cfg _mode _args]
  (try
    (let [resp (http/get {:url "http://grout.pseudovision.svc.cluster.local:8080"} {:path "/api/version"})]
      {:service "grout"
       :reachable true
       :version (:body resp)})
    (catch clojure.lang.ExceptionInfo e
      {:service "grout"
       :reachable false
       :error (ex-message e)
       :status (:status (ex-data e))})))

(defn- not-implemented [cmd]
  (fn [_cfg _mode _args]
    {:note (str "pvcli grout " cmd " is not yet implemented in v0.1")
     :todo "see https://github.com/fudoniten/pseudovision-cli/issues"}))

(def commands
  {"info"          info
   "media"         {"list"     (not-implemented "media list")
                    "get"      (not-implemented "media get")
                    "by-hash"  (not-implemented "media by-hash")}
   "intake"        (not-implemented "intake")
   "tags"          {"add"      (not-implemented "tags add")}})

(defn- lookup-handler [args]
  (let [cmd (first args)
        sub (second args)]
    (cond
      (nil? cmd)                        nil
      (contains? commands cmd)
      (let [m (get commands cmd)]
        (cond
          (map? m)            (if sub (get m sub) nil)
          (fn? m)             m
          :else               nil))
      :else                             nil)))

(defn- show-help []
  (println help)
  (System/exit 0))

(defn dispatch
  "Route a grout subcommand. `args` is the arg vector AFTER 'grout'."
  [cfg mode args]
  (cond
    (or (empty? args)
        (contains? #{"--help" "-h" "help"} (first args)))
    (show-help)

    :else
    (if-let [handler (lookup-handler args)]
      (let [result (handler cfg mode [])]
        (output/emit-and-print! result mode))
      (do (binding [*out* *err*]
            (println (str "Unknown or unimplemented grout command: "
                          (str/join " " args)))
            (println "Run 'pvcli grout --help' for the list."))
          (System/exit 2)))))
