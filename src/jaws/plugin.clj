(ns jaws.plugin
  (:require [robert.hooke :as hooke]
            [leiningen.compile :as lc]
            [leiningen.core.eval :as lce]
            [leiningen.core.main :as lcm]))

(defn hooks []
  (hooke/add-hook #'lc/compile
                  (fn lc-compile
                    ([f project]
                     (if-let [namespaces (seq (lc/stale-namespaces project))]
                       (let [ns-sym (gensym "namespace")
                             form `(doseq [~ns-sym '~namespaces]
                                     ~(if lcm/*info*
                                        `(binding [*out* *err*]
                                           (println "Compiling" ~ns-sym)))
                                     (try
                                       (clojure.core/compile ~ns-sym)
                                       (catch Throwable t#
                                         (.printStackTrace t#)
                                         (throw t#))
                                       (finally
                                         (shutdown-agents))))
                             project (update-in project [:prep-tasks]
                                                (partial remove #{"compile"}))]
                         (try (lce/eval-in-project project form)
                              (catch Exception e
                                (lcm/abort "Compilation failed:" (.getMessage e)))
                              (finally (lc/clean-non-project-classes project))))
                       (lcm/debug "All namespaces already AOT compiled.")))
                    ([f project & args]
                     (lc-compile (assoc project :aot (lc/compilation-specs args)))))))
