(ns braids.init-io-spec
  (:require [speclj.core :refer :all]
            [babashka.fs :as fs]
            [braids.init-io :as init-io]
            [braids.registry :as registry]))

(describe "braids.init-io"

  (describe "parse-init-args"
    (it "parses no args"
      (should= {} (init-io/parse-init-args [])))

    (it "parses --force"
      (should= {:force? true} (init-io/parse-init-args ["--force"])))

    (it "parses --projects-home"
      (should= {:projects-home "/tmp/p"} (init-io/parse-init-args ["--projects-home" "/tmp/p"]))))

  (describe "run-init (filesystem integration)"
    (with-all tmp-dir (str (fs/create-temp-dir {:prefix "braids-init-test"})))
    (with-all braids-dir (str @tmp-dir "/braids"))
    (with-all projects-home (str @tmp-dir "/projects"))
    (with-all registry-path (str @tmp-dir "/braids/registry.edn"))

    (it "creates braids dir, projects home, and registry on fresh install"
      (let [result (init-io/run-init [] {:braids-dir @braids-dir
                                          :projects-home @projects-home
                                          :registry-path @registry-path})]
        (should= 0 (:exit result))
        (should (fs/exists? @braids-dir))
        (should (fs/exists? @projects-home))
        (should (fs/exists? @registry-path))
        (let [reg (registry/parse-registry (slurp @registry-path))]
          (should= [] (:projects reg)))))

    (it "fails if already initialized without --force"
      ;; First init
      (init-io/run-init [] {:braids-dir @braids-dir
                             :projects-home @projects-home
                             :registry-path @registry-path})
      ;; Second init should fail
      (let [result (init-io/run-init [] {:braids-dir @braids-dir
                                          :projects-home @projects-home
                                          :registry-path @registry-path})]
        (should= 1 (:exit result))
        (should-contain "already initialized" (:message result))))

    (it "succeeds with --force even if already initialized"
      ;; First init
      (init-io/run-init [] {:braids-dir @braids-dir
                             :projects-home @projects-home
                             :registry-path @registry-path})
      ;; Second init with --force
      (let [result (init-io/run-init ["--force"] {:braids-dir @braids-dir
                                                    :projects-home @projects-home
                                                    :registry-path @registry-path})]
        (should= 0 (:exit result))))))
