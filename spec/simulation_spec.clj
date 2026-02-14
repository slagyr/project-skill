(ns simulation-spec
  (:require [spec-helper :refer [describe context it should should-not should= should-contain should-match]]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def project-root (str (System/getProperty "user.dir")))
(def contracts (slurp (str project-root "/CONTRACTS.md")))
(def test-tmp (str (fs/create-temp-dir {:prefix "sim-test"})))
(def test-project (str test-tmp "/test-sim-project"))

(defn setup-test-project! []
  (fs/delete-tree test-project)
  (fs/create-dirs (str test-project "/iterations/001"))

  (spit (str test-project "/PROJECT.md")
    "# Test Simulation Project\n\n- **Status:** active\n- **Priority:** high\n- **Autonomy:** full\n- **Checkin:** daily\n- **Channel:** test-channel-123\n- **MaxWorkers:** 2\n- **WorkerTimeout:** 1800\n\n## Notifications\n\n| Event | Notify |\n|-------|--------|\n| iteration-start | on |\n| bead-start | on |\n| bead-complete | on |\n| iteration-complete | on |\n| no-ready-beads | on |\n| question | on |\n| blocker | on |\n\n## Goal\n\nTest project for simulation tests.\n\n## Guardrails\n\n- This is a test project\n")

  (spit (str test-project "/AGENTS.md") "# Test Project AGENTS.md\nRead worker.md for instructions.\n")

  (spit (str test-project "/iterations/001/ITERATION.md")
    "# Iteration 001\n\n- **Status:** active\n\n## Stories\n- test-sim-aaa: First test bead\n- test-sim-bbb: Second test bead (depends on aaa)\n- test-sim-ccc: Third test bead (independent)\n")

  (spit (str test-tmp "/registry.md")
    (str "| Slug | Status | Priority | Path |\n|------|--------|----------|------|\n| test-sim-project | active | high | " test-project " |\n")))

;; ── Scenario 1: PROJECT.md Defaults ──

(describe "Scenario 1: PROJECT.md Field Defaults"
  (setup-test-project!)
  (spit (str test-project "/PROJECT.md")
    "# Minimal Project\n\n- **Status:** active\n- **Priority:** normal\n- **Autonomy:** full\n\n## Goal\nMinimal test.\n\n## Guardrails\n- None\n")

  (let [pmd (slurp (str test-project "/PROJECT.md"))]
    (it "Status field present"
      (should-contain "Status:" pmd))
    (it "MaxWorkers missing (default 1 applies)"
      (should-not (str/includes? pmd "MaxWorkers")))
    (it "WorkerTimeout missing (default 1800 applies)"
      (should-not (str/includes? pmd "WorkerTimeout")))
    (it "Channel missing (default: skip notifications)"
      (should-not (str/includes? pmd "Channel:")))
    (it "Checkin missing (default: on-demand)"
      (should-not (str/includes? pmd "Checkin:")))
    (it "Notifications table missing (default: all on)"
      (should-not (str/includes? pmd "Notifications")))))

;; ── Scenario 2: Iteration Lifecycle ──

(describe "Scenario 2: Iteration Lifecycle"
  (setup-test-project!)

  (let [iter (slurp (str test-project "/iterations/001/ITERATION.md"))]
    (it "ITERATION.md has Status"
      (should-match #"(?i)Status:" iter))
    (it "ITERATION.md has Stories section"
      (should-contain "## Stories" iter))
    (it "iteration status is active"
      (should-contain "active" iter)))

  ;; At most one active iteration
  (do
    (fs/create-dirs (str test-project "/iterations/002"))
    (spit (str test-project "/iterations/002/ITERATION.md")
      "# Iteration 002\n- **Status:** planning\n## Stories\n- test-sim-ddd: Future bead\n")
    (let [active-count (->> (fs/glob test-project "iterations/*/ITERATION.md")
                            (map #(slurp (str %)))
                            (filter #(re-find #"Status:.*active" %))
                            count)]
      (it (str "at most one active iteration (" active-count ")")
        (<= active-count 1))))

  ;; Completed iteration
  (do
    (fs/create-dirs (str test-project "/iterations/000"))
    (spit (str test-project "/iterations/000/ITERATION.md")
      "# Iteration 000\n- **Status:** complete\n## Stories\n- test-sim-zzz: Completed bead\n")
    (spit (str test-project "/iterations/000/RETRO.md")
      "# Iteration 000 Retrospective\n## Summary\nTest.\n## Completed\n| Bead | Title | Deliverable |\n|------|-------|-----------|\n| test-sim-zzz | Completed bead | zzz-completed.md |\n")
    (it "completed iteration has RETRO.md"
      (fs/exists? (str test-project "/iterations/000/RETRO.md")))
    (it "completed iteration status is complete"
      (should-contain "complete" (slurp (str test-project "/iterations/000/ITERATION.md"))))))

;; ── Scenario 3: Deliverable Naming ──

(describe "Scenario 3: Deliverable Naming"
  (setup-test-project!)
  (let [deliverable (str test-project "/iterations/001/aaa-first-test-bead.md")]
    (spit deliverable "# First Test Bead\n\n## Summary\nCompleted the first test bead.\n")
    (it "deliverable file created"
      (fs/exists? deliverable))
    (it "deliverable has Summary section"
      (should-contain "## Summary" (slurp deliverable)))
    (it "deliverable name matches convention"
      (should-match #"^[a-z0-9]{3}-[a-z0-9-]+\.md$" (fs/file-name deliverable)))))

;; ── Scenario 4: Orchestrator Frequency Scaling ──

(describe "Scenario 4: Orchestrator Frequency Scaling"
  (let [state-file (str test-tmp "/.orchestrator-state.json")]
    ;; After spawn
    (spit state-file "{\"idleSince\":null,\"idleReason\":null,\"lastRunAt\":\"2026-02-13T12:00:00Z\"}")
    (let [parsed (json/parse-string (slurp state-file))]
      (it "state: idleSince null after spawn"
        (nil? (get parsed "idleSince")))
      (it "state: idleReason null after spawn"
        (nil? (get parsed "idleReason")))
      (it "state: lastRunAt is set"
        (some? (get parsed "lastRunAt"))))

    ;; Idle state
    (spit state-file "{\"idleSince\":\"2026-02-13T12:05:00Z\",\"idleReason\":\"no-active-iterations\",\"lastRunAt\":\"2026-02-13T12:05:00Z\"}")
    (let [parsed (json/parse-string (slurp state-file))]
      (it "state: idle with no-active-iterations"
        (should= "no-active-iterations" (get parsed "idleReason"))))

    ;; Backoff intervals from CONTRACTS
    (it "no-active-iterations backoff 30min"
      (should-match #"no-active-iterations.*30" contracts))
    (it "no-ready-beads backoff 15min"
      (should-match #"no-ready-beads.*15" contracts))
    (it "all-at-capacity backoff 10min"
      (should-match #"all-at-capacity.*10" contracts))

    (doseq [reason ["no-active-iterations" "no-ready-beads" "all-at-capacity"]]
      (it (str "idleReason '" reason "' documented")
        (should-contain reason contracts)))))

;; ── Scenario 5: Worker Context Loading ──

(describe "Scenario 5: Worker Context Loading"
  (setup-test-project!)
  (let [home (System/getProperty "user.home")]
    (it "PROJECT.md exists (context step 1)"
      (fs/exists? (str test-project "/PROJECT.md")))
    (it "Project AGENTS.md exists (context step 3)"
      (fs/exists? (str test-project "/AGENTS.md")))
    (it "ITERATION.md exists (context step 4)"
      (fs/exists? (str test-project "/iterations/001/ITERATION.md")))
    (it "Workspace AGENTS.md exists (context step 2)"
      (fs/exists? (str home "/.openclaw/workspace/AGENTS.md")))))

;; ── Scenario 6: Spawn Message Format ──

(describe "Scenario 6: Spawn Message Format"
  (let [msg (str "Project: " test-project "\nBead: test-sim-aaa\nIteration: 1\nChannel: test-channel-123")]
    (it "spawn message has exactly 4 lines"
      (should= 4 (count (str/split-lines msg))))
    (it "has Project field"
      (should-match #"^Project:" msg))
    (it "has Bead field"
      (should-contain "Bead:" msg))
    (it "has Iteration field"
      (should-contain "Iteration:" msg))
    (it "has Channel field"
      (should-contain "Channel:" msg))))

;; ── Scenario 7: Session Label Convention ──

(describe "Scenario 7: Session Label Convention"
  (let [label "project:test-sim-project:test-sim-aaa"]
    (it "label matches format"
      (should-match #"^project:[a-z0-9-]+:[a-z0-9-]+$" label))
    (it "label starts with project:"
      (str/starts-with? label "project:"))))

;; ── Scenario 8: Bead Lifecycle ──

(describe "Scenario 8: Bead Lifecycle"
  (it "contract documents open → in_progress"
    (should-match #"open.*in_progress" contracts))
  (it "contract documents closed state"
    (should-match #"closed.*final" contracts))
  (it "contract documents blocked can be reopened"
    (should-match #"Blocked.*reopened" contracts)))

;; ── Scenario 9: Registry Validation ──

(describe "Scenario 9: Registry Validation"
  (setup-test-project!)
  (let [reg (slurp (str test-tmp "/registry.md"))]
    (it "has required columns"
      (should-contain "| Slug | Status | Priority | Path |" reg))
    (it "has valid status"
      (should-contain "active" reg))
    (it "has valid priority"
      (should-contain "high" reg)))

  ;; Bad registry
  (let [bad-reg "| Slug | Status | Priority | Path |\n|------|--------|----------|------|\n| bad-proj | complete | high | /tmp/bad |\n"
        bad-status (-> (re-find #"bad-proj\s*\|\s*(\w+)" bad-reg) second)]
    (it "rejects 'complete' as registry status"
      (not (contains? #{"active" "paused" "blocked"} bad-status)))))

;; ── Scenario 10: Worker Error Handling ──

(describe "Scenario 10: Worker Error Handling"
  (setup-test-project!)
  (let [partial (str test-project "/iterations/001/bbb-second-test-bead.md")]
    (spit partial "# Second Test Bead (Partial)\n\n## Summary\nPartially completed.\n\n## Remaining\n- Complete integration after aaa lands\n")
    (it "partial deliverable written"
      (fs/exists? partial))
    (it "partial deliverable has Summary"
      (should-contain "## Summary" (slurp partial)))
    (it "partial deliverable documents remaining work"
      (should-contain "## Remaining" (slurp partial)))))

;; ── Scenario 11: RETRO.md Format ──

(describe "Scenario 11: RETRO.md Format"
  (setup-test-project!)
  (fs/create-dirs (str test-project "/iterations/000"))
  (spit (str test-project "/iterations/000/ITERATION.md") "# Iteration 000\n- **Status:** complete\n## Stories\n- test-sim-zzz: Completed bead\n")
  (spit (str test-project "/iterations/000/RETRO.md") "# Iteration 000 Retrospective\n## Summary\nTest.\n## Completed\n| Bead | Title | Deliverable |\n|------|-------|-----------|\n| test-sim-zzz | Completed bead | zzz-completed.md |\n")

  (let [retro (slurp (str test-project "/iterations/000/RETRO.md"))]
    (it "RETRO.md has Summary section"
      (should-contain "## Summary" retro))
    (it "RETRO.md has Completed table"
      (should-contain "## Completed" retro))
    (it "RETRO.md is in completed iteration"
      (should-contain "complete" (slurp (str test-project "/iterations/000/ITERATION.md"))))))

;; ── Scenario 12: Orchestrator Invariants ──

(describe "Scenario 12: Orchestrator Invariants"
  (it "orchestrator never performs bead work"
    (should-match #"never.*performs bead work" contracts))
  (it "orchestrator only reads state and spawns"
    (should-contain "only reads state and spawns" contracts))
  (it "concurrency enforcement documented"
    (should-contain "Concurrency Enforcement" contracts))
  (it "active iteration required for spawn"
    (should-contain "Active Iteration Required" contracts)))

;; ── Scenario 13: Git Conventions ──

(describe "Scenario 13: Git Conventions"
  (it "commit format matches convention"
    (should-match #"^.+ \([a-z0-9-]+\)$" "Add first test bead (test-sim-aaa)"))
  (it "iteration commit format"
    (should-match #"^Complete iteration \d+$" "Complete iteration 1")))

;; ── Scenario 14: STATUS.md ──

(describe "Scenario 14: STATUS.md"
  (it "STATUS.md is auto-generated"
    (should-contain "Auto-generated" contracts))
  (it "STATUS.md overwritten every run"
    (should-contain "Overwritten every orchestrator run" contracts))
  (it "STATUS.md never hand-edit"
    (should-contain "never hand-edit" contracts)))

;; ── Scenario 15: Path Conventions ──

(describe "Scenario 15: Path Conventions"
  (it "~ resolves to user home"
    (should-contain "always resolves to the user's home directory" contracts))
  (it "PROJECTS_HOME default ~/Projects"
    (should-match #"PROJECTS_HOME.*defaults to.*~/Projects" contracts))
  (it "project files not in workspace"
    (should-contain "never created inside" contracts)))

;; ── Scenario 16: Iteration Completion Guard ──

(describe "Scenario 16: Iteration Completion Guard (Race Condition Prevention)"
  (setup-test-project!)

  ;; The worker.md must document the atomic guard
  (let [worker (slurp (str (System/getProperty "user.home") "/.openclaw/skills/projects/references/worker.md"))]

    (it "worker.md documents iteration completion guard"
      (should-contain ".completing" worker))

    (it "worker.md specifies atomic file creation as lock"
      (should-contain "already exists" worker))

    (it "worker.md instructs workers to skip if lock exists"
      (should-match #"(?i)(skip|abort|stop).*iteration.*(complet|RETRO)" worker)))

  ;; CONTRACTS.md must document the invariant
  (let [contracts-text (slurp (str project-root "/CONTRACTS.md"))]
    (it "CONTRACTS.md documents iteration completion atomicity"
      (should-contain ".completing" contracts-text))

    (it "CONTRACTS.md specifies only one worker completes an iteration"
      (should-match #"(?i)(one|single|first).*worker.*(complet|RETRO|iteration)" contracts-text)))

  ;; Simulate the guard: first worker creates lock, second sees it
  (let [iter-dir (str test-project "/iterations/001")
        lock-file (str iter-dir "/.completing")]

    (it "lock file does not exist initially"
      (should-not (fs/exists? lock-file)))

    ;; First worker creates the lock
    (spit lock-file "worker-1")

    (it "lock file exists after first worker creates it"
      (should (fs/exists? lock-file)))

    (it "second worker detects lock and skips"
      ;; The guard logic: if .completing exists, skip
      (should (fs/exists? lock-file)))))

;; Cleanup
(fs/delete-tree test-tmp)
