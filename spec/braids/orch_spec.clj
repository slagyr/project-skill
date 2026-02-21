(ns braids.orch-spec
  (:require [speclj.core :refer :all]
            [cheshire.core :as json]
            [braids.orch :as orch]))

(describe "braids.orch"

  (describe "tick (pure logic)"

    (it "returns idle with no-active-iterations when no projects"
      (let [result (orch/tick {:projects []} {} {} {} {} {})]
        (should= "idle" (:action result))
        (should= "no-active-iterations" (:reason result))))

    (it "returns idle with no-active-iterations when projects exist but none have active iterations"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            iterations {} ;; no active iteration found
            result (orch/tick registry {} iterations {} {} {})]
        (should= "idle" (:action result))
        (should= "no-active-iterations" (:reason result))))

    (it "returns idle with no-ready-beads when active iteration but no beads"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" []}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should= "idle" (:action result))
        (should= "no-ready-beads" (:reason result))))

    (it "returns idle with all-at-capacity when beads exist but workers full"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {"proj" 1}
            result (orch/tick registry configs iterations beads workers {})]
        (should= "idle" (:action result))
        (should= "all-at-capacity" (:reason result))))

    (it "returns spawn with bead details when work is available"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"
                             :worker-timeout 3600}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should= "spawn" (:action result))
        (should= 1 (count (:spawns result)))
        (let [spawn (first (:spawns result))]
          (should= "proj" (:project spawn))
          (should= "proj-abc" (:bead spawn))
          (should= "008" (:iteration spawn))
          (should= "123" (:channel spawn))
          (should= "/tmp/proj" (:path spawn))
          (should= "project:proj:proj-abc" (:label spawn))
          (should= 3600 (:worker-timeout spawn)))))

    (it "limits spawns to available capacity per project"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 2 :channel "123"
                             :worker-timeout 3600}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-a" :title "A" :priority "P0"}
                           {:id "proj-b" :title "B" :priority "P1"}
                           {:id "proj-c" :title "C" :priority "P2"}]}
            workers {"proj" 1}
            result (orch/tick registry configs iterations beads workers {})]
        (should= "spawn" (:action result))
        (should= 1 (count (:spawns result)))
        (should= "proj-a" (:bead (first (:spawns result))))))

    (it "spawns from multiple projects"
      (let [registry {:projects [{:slug "alpha" :status :active :priority :high :path "/tmp/alpha"}
                                  {:slug "beta" :status :active :priority :normal :path "/tmp/beta"}]}
            configs {"alpha" {:name "Alpha" :status :active :max-workers 1 :channel "111"
                              :worker-timeout 3600}
                     "beta" {:name "Beta" :status :active :max-workers 1 :channel "222"
                             :worker-timeout 3600}}
            iterations {"alpha" "003" "beta" "001"}
            beads {"alpha" [{:id "alpha-a" :title "Alpha task" :priority "P0"}]
                   "beta" [{:id "beta-b" :title "Beta task" :priority "P1"}]}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should= "spawn" (:action result))
        (should= 2 (count (:spawns result)))))

    (it "skips paused projects from config"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :paused :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should= "idle" (:action result))
        (should= "no-active-iterations" (:reason result))))

    (it "uses default worker-timeout when not specified"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should= 1800 (:worker-timeout (first (:spawns result))))))

    (it "returns disable-cron true when idle with no-active-iterations"
      (let [result (orch/tick {:projects []} {} {} {} {} {})]
        (should= true (:disable-cron result))))

    (it "returns disable-cron true when idle with no-ready-beads"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" []}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should= true (:disable-cron result))))

    (it "returns disable-cron true when idle with all-at-capacity"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {"proj" 1}
            result (orch/tick registry configs iterations beads workers {})]
        (should= true (:disable-cron result))))

    (it "does not include disable-cron when spawning work"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {}
            result (orch/tick registry configs iterations beads workers {})]
        (should-not-contain :disable-cron result)))

    (it "includes notification-mentions in spawn when present"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"
                             :worker-timeout 3600
                             :notifications {:bead-start true :no-ready-beads true}
                             :notification-mentions {:iteration-complete "<@user>"}}}
            iterations {"proj" "008"}
            beads {"proj" [{:id "proj-abc" :title "Do stuff" :priority "P1"}]}
            workers {}
            notifications {"proj" {:notifications {:bead-start true :no-ready-beads true}
                                   :notification-mentions {:iteration-complete "<@user>"}}}
            result (orch/tick registry configs iterations beads workers notifications)]
        (should= "spawn" (:action result)))))

  (describe "format-tick-json"

    (it "formats spawn result as JSON"
      (let [result {:action "spawn"
                    :spawns [{:project "proj" :bead "proj-abc" :iteration "008"
                              :channel "123" :path "/tmp/proj"
                              :label "project:proj:proj-abc"
                              :worker-timeout 3600}]}
            json-str (orch/format-tick-json result)]
        (should-contain "\"action\":\"spawn\"" json-str)
        (should-contain "proj-abc" json-str)))

    (it "formats idle result as JSON"
      (let [result {:action "idle" :reason "no-ready-beads"}
            json-str (orch/format-tick-json result)]
        (should-contain "\"action\":\"idle\"" json-str)
        (should-contain "no-ready-beads" json-str))))

  (describe "format-orch-run-json"

    (it "formats idle result with reason"
      (let [tick-result {:action "idle" :reason "no-ready-beads" :disable-cron true}
            json-str (orch/format-orch-run-json tick-result)
            parsed (json/parse-string json-str true)]
        (should= "idle" (:action parsed))
        (should= "no-ready-beads" (:reason parsed))
        (should= true (:disable_cron parsed))
        (should-not-contain :spawns parsed)))

    (it "formats spawn result with sessions_spawn-ready entries"
      (let [tick-result {:action "spawn"
                         :spawns [{:project "proj"
                                   :bead "proj-abc"
                                   :iteration "008"
                                   :channel "123"
                                   :path "/tmp/proj"
                                   :label "project:proj:proj-abc"
                                   :worker-timeout 3600}]}
            json-str (orch/format-orch-run-json tick-result)
            parsed (json/parse-string json-str true)
            spawn (first (:spawns parsed))]
        (should= "spawn" (:action parsed))
        (should= 1 (count (:spawns parsed)))
        (should-contain "Read and follow" (:task spawn))
        (should-contain "Project: /tmp/proj" (:task spawn))
        (should= "project:proj:proj-abc" (:label spawn))
        (should= 3600 (:runTimeoutSeconds spawn))
        (should= "delete" (:cleanup spawn))
        (should= "low" (:thinking spawn))))

    (it "formats multiple spawns"
      (let [tick-result {:action "spawn"
                         :spawns [{:project "a" :bead "a-1" :iteration "001"
                                   :channel "111" :path "/tmp/a"
                                   :label "project:a:a-1" :worker-timeout 1800}
                                  {:project "b" :bead "b-2" :iteration "002"
                                   :channel "222" :path "/tmp/b"
                                   :label "project:b:b-2" :worker-timeout 3600}]}
            json-str (orch/format-orch-run-json tick-result)
            parsed (json/parse-string json-str true)]
        (should= 2 (count (:spawns parsed)))
        (should= "project:a:a-1" (:label (first (:spawns parsed))))
        (should= "project:b:b-2" (:label (second (:spawns parsed)))))))

  (describe "no-ready-beads-projects"

    (it "identifies projects with active iterations but no ready beads"
      (let [registry {:projects [{:slug "proj" :status :active :priority :normal :path "/tmp/proj"}]}
            configs {"proj" {:name "Proj" :status :active :max-workers 1 :channel "123"
                             :notifications {:no-ready-beads true}}}
            iterations {"proj" "008"}
            beads {"proj" []}
            workers {}]
        (should= ["proj"] (orch/no-ready-beads-projects registry configs iterations beads workers))))))
