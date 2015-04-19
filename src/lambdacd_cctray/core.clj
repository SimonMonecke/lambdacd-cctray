(ns lambdacd-cctray.core
  (:require [clojure.data.xml :as xml]
            [lambdacd.presentation.pipeline-structure :as lp]
            [clj-time.format :as f ]))

(defn- has-step-id [step-id [k v]]
  (get v step-id)
)

(defn state-for [step-id [k v]]
  (let [build-number k
        status (get v step-id)
        activity (if (= (:status status) :running) "Building" "Sleeping")]
    {:build-number build-number
     :activity activity
     :result status}))

(defn- states-for [step-id state]
  (let [builds-with-step-id (filter #(has-step-id step-id %) (seq state))
        by-most-recent (reverse (sort-by first builds-with-step-id))]
    (map #(state-for step-id %) by-most-recent)))

(defn- last-build-status-for [states-for-step]
  (let [current-build (:result (first states-for-step))
        prev-build (:result (second states-for-step))
        cur-build-status (:status current-build)
        build-to-use (if (or (= cur-build-status :running) (= cur-build-status :waiting))
                       prev-build
                       current-build)
        status-to-use (:status build-to-use)]
    (case status-to-use
      :success "Success"
      :failure "Failure"
      "Unknown")))

(defn- last-build-time-for [step]
  (let [most-recent-update (:most-recent-update-at (:result step))
        formatted (f/unparse (f/formatters :date-time) most-recent-update)]
    formatted))

(defn- project-for [state base-url step-info]
  (let [states-for-step (states-for (:step-id step-info) state)
        state-for-step (first states-for-step)
        last-build-number (:build-number state-for-step)]
    (xml/element :Project {:name (:name step-info)
                           :activity (:activity state-for-step)
                           :lastBuildStatus (last-build-status-for states-for-step)
                           :lastBuildLabel (str last-build-number)
                           :lastBuildTime (last-build-time-for state-for-step)
                           :webUrl (str base-url "/old/?build=" last-build-number)} [])))

(defn- flatten-pipeline [pipeline-representation]
  (let [children-reps (flatten (map #(flatten-pipeline (:children %)) pipeline-representation))]
    (concat pipeline-representation children-reps)))

(defn- projects-for [def state base-url]
  (let [pipeline-representation (flatten-pipeline (lp/display-representation def))]
    (map (partial project-for state base-url) pipeline-representation)))

(defn cctray-xml-for [pipeline-def pipeline-state base-url]
  (xml/emit-str
    (xml/element :Projects {} (projects-for pipeline-def pipeline-state base-url))))

(defn cctray-handler-for [pipeline-def state-atom base-url]
  (fn [& _]
    {:status  200
     :headers {"Content-Type" "application/xml"}
     :body    (cctray-xml-for pipeline-def @state-atom base-url)}))

