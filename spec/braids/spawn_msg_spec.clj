(ns braids.spawn-msg-spec
  (:require [speclj.core :refer :all]
            [cheshire.core :as json]
            [braids.orch :as orch]))

(describe "braids.orch/spawn-msg"

  (it "generates spawn message with worker.md instruction prefix"
    (let [spawn {:project "my-proj"
                 :bead "my-proj-abc"
                 :iteration "008"
                 :channel "123456"
                 :path "/home/user/Projects/my-proj"
                 :label "project:my-proj:my-proj-abc"
                 :worker-timeout 3600}]
      (should= (str "You are a project worker for the braids skill. "
                     "Read and follow ~/.openclaw/skills/braids/references/worker.md\n\n"
                     "Project: /home/user/Projects/my-proj\n"
                     "Bead: my-proj-abc\n"
                     "Iteration: 008\n"
                     "Channel: 123456")
               (orch/spawn-msg spawn))))

  (it "handles empty channel"
    (let [spawn {:project "proj"
                 :bead "proj-xyz"
                 :iteration "001"
                 :channel ""
                 :path "/tmp/proj"
                 :label "project:proj:proj-xyz"
                 :worker-timeout 3600}]
      (should-contain "Channel: " (orch/spawn-msg spawn))
      (should-contain "Read and follow" (orch/spawn-msg spawn))))

  (describe "format-spawn-msg-json"

    (it "returns JSON with task, label, runTimeoutSeconds, cleanup, and thinking"
      (let [spawn {:project "proj"
                   :bead "proj-abc"
                   :iteration "008"
                   :channel "123"
                   :path "/tmp/proj"
                   :label "project:proj:proj-abc"
                   :worker-timeout 3600}
            json-str (orch/format-spawn-msg-json spawn)
            parsed (json/parse-string json-str true)]
        (should-contain "Read and follow" (:task parsed))
        (should-contain "Project: /tmp/proj" (:task parsed))
        (should= "project:proj:proj-abc" (:label parsed))
        (should= 3600 (:runTimeoutSeconds parsed))
        (should= "delete" (:cleanup parsed))
        (should= "low" (:thinking parsed))
        (should-be-nil (:message parsed))
        (should-be-nil (:worker_timeout parsed))))))
