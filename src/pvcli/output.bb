(ns pvcli.output
  "Output formatting. JSON by default, --human for tables.

   The shape contract:
   - (emit data :json)  →  a JSON string (the default, pipeable to jq)
   - (emit data :human) →  a human-readable string for the terminal

   For `:human` mode, we use a simple heuristic:
   - If `data` is a vector of maps with shared keys → render as a table.
   - If `data` is a single map → render as key: value pairs.
   - If `data` is anything else → pretty-print as JSON.
   - Empty collections → \"(no results)\"."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn- cell
  "Render a single value for human output. Collections become compact JSON
   so a table/kv cell never shows a raw Clojure literal."
  [v]
  (cond
    (nil? v)  ""
    (coll? v) (json/generate-string v)
    :else     (str v)))

(defn- kv-block [m]
  (let [ks (sort (keys m))
        w  (apply max 0 (map count (map name ks)))]
    (str/join "\n"
              (for [k ks]
                (format (str "%-" w "s  %s") (name k) (cell (get m k)))))))

(defn- table [rows]
  (let [ks  (->> rows first keys vec)
        val-strs (fn [k] (for [r rows] (cell (get r k))))
        widths   (zipmap ks
                         (for [k ks]
                           (apply max (count (name k))
                                  (map count (val-strs k)))))
        fmt-row  (fn [r]
                   (str/join "  "
                             (for [k ks]
                               (format (str "%-" (widths k) "s")
                                       (cell (get r k))))))
        header   (fmt-row (zipmap ks (map name ks)))
        sep      (apply str (repeat (count header) \-))
        body     (str/join "\n" (map fmt-row rows))]
    (str header "\n" sep "\n" body)))

(defn- humanize [data]
  (cond
    (nil? data)                            "(null)"
    (and (sequential? data) (empty? data)) "(no results)"
    (and (sequential? data)
         (every? map? data))               (table data)
    (map? data)
    (let [items (:items data)]
      ;; Common paginated shape: {:items [...]} → table the items.
      (cond
        (and (sequential? items) (every? map? items) (seq items))
        (table items)

        (and (sequential? items) (empty? items))
        "(no results)"

        :else (kv-block data)))
    (sequential? data)                     (table (vec (for [x data] {:value x})))
    :else                                  (json/generate-string data {:pretty true})))

(defn emit
  "Format `data` for output. `mode` is :json (default) or :human."
  [data mode]
  (case mode
    :json   (json/generate-string data)
    :human  (humanize data)
    (json/generate-string data)))

(defn emit-and-print!
  "Format and print to stdout. Returns nil."
  [data mode]
  (println (emit data mode))
  nil)
