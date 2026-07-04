;; Test runner. Loads all *_test.bb files in this directory and prints a
;; summary. Exits 0 if all pass, 1 if any fail.
;;
;; Tests use the minimal `pvcli.assert` library — no external deps, no
;; classpath config. Each test file should require `pvcli.assert` and
;; call `pvcli.assert/summary` (implicitly via run-tests) at the end.
;;
;; Run from repo root:  bb tests/run.bb
;; Or via nix flake check.

(require '[babashka.classpath :as cp]
         '[babashka.fs :as fs])

;; Add the tests/ and src/ dirs to the classpath. bb doesn't do this
;; automatically when running a script — see
;; https://book.babashka.org/#classpath. We do this BEFORE requiring
;; pvcli.assert, which lives in tests/pvcli/.
(cp/add-classpath (-> (fs/file *file*) fs/parent str))
(cp/add-classpath (-> (fs/file *file*) fs/parent fs/parent (str "/src")))

;; Now safe to require pvcli.* namespaces.
(require '[pvcli.assert :as a])

(def test-dir (fs/path (fs/file *file*) ".."))

(def test-files
  (->> (fs/glob test-dir "*_test.bb")
       (map str)
       sort))

(a/clear!)

(doseq [f test-files]
  (println "==> Running" f)
  (try
    (load-file f)
    (catch Exception e
      (println "  EXCEPTION during load:" (str e))
      (a/truthy false (str "load " f " — " (ex-message e))))))

(let [{:keys [pass fail errors]} (a/summary)]
  (println)
  (println (str "Results: " pass " pass, " fail " fail"))
  (when (seq errors)
    (println "Details:")
    (doseq [{:keys [msg]} errors]
      (println " -" msg)))
  (System/exit (if (zero? fail) 0 1)))
