(ns pvcli.grout
  "Grout subcommands (filler media store).

   For v0.1, only `info` is wired up. The `intake` command (lifted from
   the original `grout-cli.bb` in the Grout repo, but updated for the
   multipart intake contract) and the media/tags queries land in a later
   PR — their entries below return a not-implemented stub so the command
   surface and help are stable.

   Uses the same command-tree model as pv/ts (see pvcli.command)."
  (:require [clojure.string :as str]
            [pvcli.command :as command]
            [pvcli.http :as http]))

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
  "Service info for grout. Uses the resolved grout config (env / config file
   / built-in default), not a hardcoded URL. HTTP failures propagate."
  [{:keys [cfg]}]
  (:body (http/get (:grout cfg) {:path "/api/version"})))

(defn- not-implemented [cmd]
  (fn [_]
    {:note (str "pvcli grout " cmd " is not yet implemented in v0.1")
     :todo "see https://github.com/fudoniten/pseudovision-cli/issues"}))

(def commands
  {"info"
   {:help-summary "Service info (build, version, route count)"
    :spec {}
    :handler info}

   "media"
   {:help-summary "Filler media commands"
    :sub
    {"list"    {:help-summary "List filler media (filter by channel/tag/duration)"
                :spec {:channel "Filter by channel name"
                       :tag     "Filter by tag"}
                :handler (not-implemented "media list")}
     "get"     {:help-summary "Fetch one media item by uuid"
                :spec {}
                :handler (not-implemented "media get")}
     "by-hash" {:help-summary "Look up a media item by content hash"
                :spec {}
                :handler (not-implemented "media by-hash")}}}

   "intake"
   {:help-summary "Upload+tag one or more files (multipart)"
    :spec {}
    :handler (not-implemented "intake")}

   "tags"
   {:help-summary "Tag commands"
    :sub
    {"add" {:help-summary "Add a tag to an existing media item"
            :spec {}
            :handler (not-implemented "tags add")}}}})

(defn dispatch
  "Route a grout subcommand. `args` is the arg vector AFTER 'grout'.
   `cfg` is the resolved config. `mode` is :json or :human."
  [cfg mode args]
  (command/dispatch "grout" help commands cfg mode args))
