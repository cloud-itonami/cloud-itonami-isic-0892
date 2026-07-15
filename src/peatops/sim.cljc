(ns peatops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean extraction-record
  logging request through intake -> advise -> govern -> decide ->
  approval -> commit at phase 1 (assisted-extraction, always
  approval), then re-runs the same op at phase 3 (supervised-auto,
  clean + high confidence -> auto-commit), then an extraction-operation
  scheduling request and a shipment-coordination request (also
  auto-commit clean at phase 3), then an environmental-concern flag
  (ALWAYS escalates, at any phase -- approve, then commit), then
  HARD-hold scenarios: an unregistered site, a site registered but not
  yet verified, a proposal whose own `:effect` is not `:propose`, and a
  proposal that has drifted into the permanently-excluded extraction-
  equipment-control/environmental-permit-issuing-authority scope."
  (:require [langgraph.graph :as g]
            [peatops.advisor :as advisor]
            [peatops.store :as store]
            [peatops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "site-supervisor-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        operator-phase-1 {:actor-id "op-1" :actor-role :site-supervisor :phase 1}
        operator-phase-3 {:actor-id "op-1" :actor-role :site-supervisor :phase 3}
        actor (op/build db)]

    (println "== log-extraction-record peat-site-1 (phase 1, escalates -- human approves) ==")
    (println (exec-op actor "t1" {:op :log-extraction-record :site-id "peat-site-1"
                                  :patch {:volume-m3 420 :moisture-pct 32 :shift "day"}} operator-phase-1))
    (println (approve! actor "t1"))

    (println "== log-extraction-record peat-site-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-extraction-record :site-id "peat-site-1"
                                  :patch {:volume-m3 450 :moisture-pct 30 :shift "night"}} operator-phase-3))

    (println "== schedule-extraction-operation peat-site-2 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :schedule-extraction-operation :site-id "peat-site-2"
                                  :patch {:activity "sod-cutting" :window "2026-07-20"}} operator-phase-3))

    (println "== coordinate-shipment peat-site-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :coordinate-shipment :site-id "peat-site-1"
                                  :patch {:carrier "rail-co-1" :bales 800}} operator-phase-3))

    (println "== flag-environmental-concern peat-site-2 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t5" {:op :flag-environmental-concern :site-id "peat-site-2"
                                 :patch {:concern "elevated runoff near drainage ditch, possible wetland impact" :confidence 0.95}} operator-phase-3)]
      (println r)
      (println "-- human site supervisor reviews & approves --")
      (println (approve! actor "t5")))

    (println "== log-extraction-record peat-site-9 (unregistered site -> HARD hold) ==")
    (println (exec-op actor "t6" {:op :log-extraction-record :site-id "peat-site-9"
                                  :patch {:volume-m3 10}} operator-phase-3))

    (println "== log-extraction-record peat-site-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-extraction-record :site-id "peat-site-3"
                                  :patch {:volume-m3 10}} operator-phase-3))

    (println "== coordinate-shipment peat-site-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer db req) :effect :commit)))})]
      (println (exec-op actor-direct "t8" {:op :coordinate-shipment :site-id "peat-site-1"
                                           :patch {:carrier "rail-co-1"}} operator-phase-3)))

    (println "== schedule-extraction-operation peat-site-1, advisor drifts into vacuum-harvester/drainage-ditch scope -> HARD hold, permanent ==")
    (println (exec-op actor "t9" {:op :schedule-extraction-operation :site-id "peat-site-1"
                                  :out-of-scope? true
                                  :patch {}} operator-phase-3))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))))
