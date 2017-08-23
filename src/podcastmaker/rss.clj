(ns podcastmaker.rss
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [io.pedestal.interceptor.chain :as chain]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [ring.util.response :as ring-resp]
            [ring.middleware.session.cookie :as cookie]
            [podcastmaker.login :as login]
            [podcastmaker.home :as home]
            [podcastmaker.user-data :as ud]
            [podcastmaker.layout :as h]))

(defn make-item [request file host]
  (let [path (.getName file)
        full-path (str "http://" host (:context-path request) "/content/" path)]
    (str
     "        <item>
                   <title>" path "</title>
             <enclosure url='" full-path "' length='" (.length file) "' type='audio/mpeg'/>
        </item>
        ")))

(defn make-items [request files host]
  (clojure.string/join "" (map #(make-item request % host) files)))

(defn rss-page [request]
  (let [id (get-in request [:path-params :user])
        files (ud/get-podcast-files id)
        host (get (:headers request) "host")]

    {:status 200 :headers {"Content-type" "application/xml"} :body
     (str
      "<?xml version='1.0' encoding='UTF-8'?>
               <rss version='2.0'>
               <channel>
                 <description>podcastmaker feed</description>
                 <link>https://github.com/bherrmann7/podcastmaker</link>
                 <title>PodcastMaker</title>
" (make-items request files host)  "  </channel> </rss>")}))

(defn content-page [request]
  (let [filename (get-in request [:path-params :content])
        file (io/file (ud/data-dir-file (ud/id request)) (java.net.URLDecoder/decode filename))]
    (println (str "Fetching:" file))
    (if (.exists file)
      {:status 200
       :headers {"Content-Type" "application/mp3"}
       :body (java.io.FileInputStream. file)}

      {:status 200 :headers {"Content-type" "text/plain"} :body (str "Missing: " file)})))
