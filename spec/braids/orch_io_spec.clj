(ns braids.orch-io-spec
  (:require [speclj.core :refer :all]
            [babashka.fs :as fs]
            [braids.orch-io :as oio]))

(def test-tmp (str (fs/create-temp-dir {:prefix "orch-io-test"})))

(defn make-iteration-dir! [project iter-num content]
  (let [dir (str project "/.project/iterations/" iter-num)]
    (fs/create-dirs dir)
    (spit (str dir "/ITERATION.md") content)
    dir))

(describe "braids.orch-io"

  (describe "parse-iteration-status"

    (it "parses plain 'Status: active'"
      (should= "active" (oio/parse-iteration-status "# Iteration 001\n\nStatus: active\n")))

    (it "parses markdown bold '- **Status:** active'"
      (should= "active" (oio/parse-iteration-status "# Iteration 001\n\n- **Status:** active\n")))

    (it "parses 'Status: planning'"
      (should= "planning" (oio/parse-iteration-status "# Iteration 001\n\nStatus: planning\n")))

    (it "parses 'Status: complete'"
      (should= "complete" (oio/parse-iteration-status "# Iteration 001\n\nStatus: complete\n")))

    (it "parses case-insensitively"
      (should= "active" (oio/parse-iteration-status "status: active\n")))

    (it "parses with extra whitespace"
      (should= "active" (oio/parse-iteration-status "Status:   active\n")))

    (it "parses bold without list prefix '**Status:** active'"
      (should= "active" (oio/parse-iteration-status "**Status:** active\n")))

    (it "returns nil when no status found"
      (should-be-nil (oio/parse-iteration-status "# Iteration 001\n\nNo status here\n"))))

  (describe "find-active-iteration"

    (it "finds active iteration with plain format"
      (let [project (str test-tmp "/proj1")]
        (make-iteration-dir! project "001" "# Iteration 001\n\nStatus: active\n\n## Stories\n")
        (should= "001" (oio/find-active-iteration project))))

    (it "finds active iteration with markdown bold format"
      (let [project (str test-tmp "/proj2")]
        (make-iteration-dir! project "003" "# Iteration 003\n\n- **Status:** active\n\n## Stories\n")
        (should= "003" (oio/find-active-iteration project))))

    (it "skips non-active iterations"
      (let [project (str test-tmp "/proj3")]
        (make-iteration-dir! project "001" "# Iteration 001\n\nStatus: complete\n")
        (make-iteration-dir! project "002" "# Iteration 002\n\n- **Status:** active\n")
        (should= "002" (oio/find-active-iteration project))))

    (it "returns nil when no active iteration"
      (let [project (str test-tmp "/proj4")]
        (make-iteration-dir! project "001" "# Iteration 001\n\nStatus: complete\n")
        (should-be-nil (oio/find-active-iteration project)))))

  (after-all (fs/delete-tree test-tmp)))
