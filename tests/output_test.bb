(ns pvcli.output-test
  (:require [cheshire.core :as json]
            [pvcli.assert :as a]
            [pvcli.output :as output]))

(a/truthy (= "{\"a\":1}"
          (output/emit {:a 1} :json))
     "json mode emits JSON")

(a/truthy (= "[1,2,3]"
          (output/emit [1 2 3] :json))
     "json mode handles vectors")

(a/truthy (= "null"
          (output/emit nil :json))
     "json mode handles nil")

(a/truthy (string? (output/emit [{:id 1 :name "alpha"} {:id 2 :name "beta"}] :human))
     "human mode returns a string for table data")

(let [s (output/emit [{:id 1 :name "alpha"} {:id 2 :name "beta"}] :human)]
  (a/truthy (re-find #"id\s+name" s)
       "human mode includes column header")
  (a/truthy (re-find #"alpha" s)
       "human mode includes row data")
  (a/truthy (re-find #"beta" s)
       "human mode includes all rows"))

(a/truthy (= "(no results)" (output/emit [] :human))
     "human mode handles empty vectors")

(let [s (output/emit {:a 1 :b 2} :human)]
  (a/truthy (re-find #"a\s+1" s)
       "human mode renders single map as k/v pairs")
  (a/truthy (re-find #"b\s+2" s)
       "human mode renders all keys"))

(a/truthy (= (json/generate-string {:x 1})
          (output/emit {:x 1} :unknown-mode))
     "unknown mode falls back to JSON")
