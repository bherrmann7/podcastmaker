
(ns podcastmaker.user-data
  (:require [clojure.java.io :as io]
            [ring.util.response :as ring-resp]
            ))


(defn read-data-key [] (slurp (str (System/getProperty "user.home") "/pm-data/pm-key" )))

(defn data-dir [id] (str (System/getProperty "user.home") "/pm-data/" id ))
(defn data-dir-file [id] (io/file (data-dir id)))

(defn id [request]
  (get-in request [:session :id]))

(defn flash-and-redirect [request dest msg]
  (let [current-session (:session request)]
    (println ":::::::::::;; CURRENT SESSION" current-session)
    (assoc (ring-resp/redirect dest) :session (assoc current-session :flash msg))))

(defn flash-and-redirect-to-home [request msg]
  (flash-and-redirect request "/" msg))

(defn flash-and-redirect-to-login [request msg]
  (flash-and-redirect request "/login" msg))

; copy and remove flash part... a little wonkey.. assumes copying of request session to resp session.
(defn flash-scrub [request resp]
  (let [s (assoc resp :session (dissoc (:session request) :flash))]
    s))


(defn get-podcast-files [id]
  (filter #(not (.startsWith (.getName %) "." ))
          (sort-by #(.lastModified %) > (.listFiles (data-dir-file id)))))

