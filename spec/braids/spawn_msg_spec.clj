(ns braids.spawn-msg-spec
  (:require [speclj.core :refer :all]
            [braids.orch :as orch]))

(describe "braids.orch spawn-msg removal"

  (it "worker-instruction is removed from orch namespace"
    (should-be-nil (resolve 'braids.orch/worker-instruction)))

  (it "spawn-msg is removed from orch namespace"
    (should-be-nil (resolve 'braids.orch/spawn-msg)))

  (it "format-spawn-msg-json is removed from orch namespace"
    (should-be-nil (resolve 'braids.orch/format-spawn-msg-json))))
