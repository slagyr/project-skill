(ns braids.new
  (:require [clojure.string :as str]
            [braids.project-config :as pc]))

(def slug-pattern #"^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

(defn validate-slug [slug]
  (cond
    (or (nil? slug) (str/blank? slug))
    ["Slug is required"]

    (not (re-matches slug-pattern slug))
    [(str "Invalid slug: '" slug "' — must be lowercase alphanumeric with hyphens, no leading/trailing hyphens")]

    :else []))

(defn validate-new-params [{:keys [slug name goal]}]
  (concat
    (validate-slug slug)
    (when-not name ["Name is required"])
    (when-not goal ["Goal is required"])))

(defn build-project-config [{:keys [name goal priority autonomy checkin channel
                                     max-workers worker-timeout guardrails]}]
  (merge
    {:name name
     :status :active
     :priority (or priority :normal)
     :autonomy (or autonomy :full)
     :checkin (or checkin :on-demand)
     :channel channel
     :max-workers (or max-workers 1)
     :worker-timeout (or worker-timeout 3600)
     :goal goal
     :notifications pc/default-notifications}
    (when guardrails {:guardrails guardrails})))

(defn build-registry-entry [{:keys [slug priority]} path]
  {:slug slug
   :status :active
   :priority (or priority :normal)
   :path path})

(defn build-iteration-content []
  {:number 1
   :status :planning
   :stories []})

(defn build-agents-md []
  "# AGENTS.md

This project is managed by the **braids** skill. Read `.project/PROJECT.md` for goals, guardrails, and settings.

## How to Work on This Project

**If you were spawned by the orchestrator** (your task message includes `Project:` and `Bead:` fields):
→ Follow `~/.openclaw/skills/braids/references/worker.md`

**If you're here on your own** (manual session, human asked you to help, etc.):
1. Read `.project/PROJECT.md` — understand the goal and guardrails
2. Find the active iteration: look in `.project/iterations/*/ITERATION.md` for `Status: active`
3. Run `bd ready` to see available work
4. Pick a bead, then follow the worker workflow: `~/.openclaw/skills/braids/references/worker.md`

## Quick Reference

```bash
bd ready              # List unblocked tasks
bd show <id>          # View task details
bd update <id> --claim  # Claim a task
bd update <id> -s closed  # Close completed task
bd list               # List all tasks
bd dep list <id>      # List dependencies
```

## Session Completion

Work is NOT complete until `git push` succeeds.

```bash
git add -A && git commit -m \"<summary> (<bead-id>)\"
git pull --rebase
bd sync
git push
```
")

(defn add-to-registry [registry entry]
  (let [existing-slugs (set (map :slug (:projects registry)))]
    (when (contains? existing-slugs (:slug entry))
      (throw (Exception. (str "Project '" (:slug entry) "' already exists in registry"))))
    (update registry :projects conj entry)))
