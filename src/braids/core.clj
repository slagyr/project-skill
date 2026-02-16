(ns braids.core
  (:require [clojure.string :as str]
            [braids.ready :as ready]
            [braids.ready-io :as ready-io]
            [braids.orch :as orch]
            [braids.orch-io :as orch-io]
            [braids.list-io :as list-io]
            [braids.iteration-io :as iter-io]))

(def commands
  {"list"      {:command :list      :doc "Show projects from registry"}
   "iteration" {:command :iteration :doc "Show active iteration and bead statuses"}
   "ready"     {:command :ready     :doc "List beads ready to work"}
   "orch-tick" {:command :orch-tick :doc "Orchestrator tick: compute spawn decisions (JSON)"}
   "help"      {:command :help      :doc "Show this help message"}})

(defn help-text []
  (str/join "\n"
    ["braids — CLI for the braids skill"
     ""
     "Usage: braids <command> [args...]"
     ""
     "Commands:"
     (str/join "\n"
       (for [[name {:keys [doc]}] (sort-by key commands)]
         (format "  %-12s %s" name doc)))
     ""
     "Options:"
     "  -h, --help   Show this help message"]))

(defn dispatch [args]
  (let [first-arg (first args)
        rest-args (vec (rest args))]
    (cond
      (or (nil? first-arg) (= first-arg "--help") (= first-arg "-h"))
      {:command :help}

      (contains? commands first-arg)
      (merge {:command (get-in commands [first-arg :command])
              :args rest-args})

      :else
      {:command :unknown :input first-arg})))

(defn run [args]
  (let [{:keys [command input]} (dispatch args)]
    (case command
      :help (do (println (help-text)) 0)
      :unknown (do (println (str "Unknown command: " input))
                   (println)
                   (println (help-text))
                   1)
      :list (let [json? (some #{"--json"} (:args (dispatch args)))]
              (println (list-io/load-and-list {:json? json?}))
              0)
      :iteration (let [args (:args (dispatch args))
                       json? (some #{"--json"} args)
                       ;; Use --project path or default to cwd
                       project-path (or (second (drop-while #(not= "--project" %) args))
                                        (System/getProperty "user.dir"))]
                   (println (iter-io/load-and-show {:project-path project-path :json? json?}))
                   0)
      :ready (let [result (ready-io/gather-and-compute)]
               (println (ready/format-ready-output result))
               0)
      :orch-tick (let [result (orch-io/gather-and-tick)]
                   (println (orch/format-tick-json result))
                   0)
      ;; Default for unimplemented commands
      (do (println (str "Command '" (name command) "' not yet implemented.")) 0))))
