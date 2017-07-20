(ns podcastmaker.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-mw]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [ring.util.response :as ring-resp]
            [ring.middleware.session.cookie :as cookie]
            [podcastmaker.layout :as h]))

(use 'hiccup.core)

(def data-dir (str (System/getProperty "user.home") "/pm-data"))
(def data-dir-file (io/file data-dir))

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

(defn get-podcast-files []
  (sort-by #(.lastModified %) > (.listFiles data-dir-file)))

(defn rss-page [request]
  (let [files (get-podcast-files)
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
        file (io/file data-dir-file (java.net.URLDecoder/decode filename))]
    (println (str "Fetching:" file))
    (if (.exists file)
      {:status 200
       :headers {"Content-Type" "application/mp3"}
       :body (java.io.FileInputStream. file)}

      {:status 200 :headers {"Content-type" "text/plain"} :body (str "Missing: " file)})))

(defn about-page
  [request]
  (ring-resp/response (h/layout "About" (str "Podcast Maker.   Running Clojure "
                                             (clojure-version)
                                             " from "
                                             (route/url-for ::about-page)
                                             "HOST " (get-in request [:headers "host"])))))

(defn scrub-name [x]
  (clojure.string/replace x #"[^a-zA-Z0-9_ ]" ""))

(defn download-url [url]
  (let [temp-dir (io/file (str "/tmp/" (System/currentTimeMillis)))]
    (.mkdir temp-dir)
    (println "=-=-=-=-=-=       RUNNING: " "sh/sh" "youtube-dl" "-q" "--extract-audio" "--audio-format" "mp3" url :dir (str temp-dir))
    (sh/sh "youtube-dl" "-q" "--extract-audio" "--audio-format" "mp3" url :dir (str temp-dir))
    (println "=-=-=-=-=-=       Finished with youtube-dl")
    (let [mp3-path (first (.listFiles temp-dir))
          name-scrubbed (scrub-name (.getName mp3-path))]
      (sh/sh "mv" (str mp3-path) (io/file data-dir-file name-scrubbed))
      (.delete mp3-path))))

(defn save-file [file]
  (let [filename (:filename file)
        tempfile (:tempfile file)
        destfile (io/file data-dir-file filename)]
    (io/copy tempfile destfile)))

(defn save-files [files]
  (doall (map #(save-file %) files))
  )

(defn add-page [request]
  (println "CALLED add-page params:" (:params request))
  (let [url (get (:params request) "url")
        file (get (:params request) "file")]
    (if-not (empty? url)
      (do
        (future (download-url url))
        (assoc  (ring-resp/redirect "/") :session {:flash "The audio is being downloaded.  It should be availble in a minute or two.  Reload to see it added."})))
    (if-not (empty? file)
      (if (vector? file)
        (save-files file)
        (save-file file)))
    (ring-resp/redirect "/")))

(defn list-file [file]
  (vec
   [:tr
    [:td [:a {:href (str "/content/" (.getName file))} (.getName file)]]
    [:td (format "%,d" (.length file))]
    [:td [:form {:method "POST" :action (str "/delete/" (.getName file))}
          [:input {:type "submit" :value "Delete"}]]]]))

(defn scrub-flash [resp]
  (assoc resp :session (dissoc (:session resp) :flash)))

(defn home-page
  [request]
  (let [host (get-in request [:headers "host"])
        rss-feed (str "http://" host (route/url-for ::rss-page))]
    (scrub-flash (ring-resp/response
                  (h/layout "Home"
                            (html
                             (if-not (nil? (get-in request [:session :flash]))
                               [:div.alert.alert-success (get-in request [:session :flash])])
                             [:div
                              [:div.panel.panel-default
                               [:div.panel-body
                                "The Podcast's Subcription/RSS Feed is  "
                                [:a {:href rss-feed} rss-feed]]]

                              [:div.panel.panel-default
                               [:div.panel-heading
                                [:h3.panel-title "Add Audio Content"]]
                               [:div.panel-body
                                [:div.row
                                 [:form {:method "POST" :action "/add" :enctype "multipart/form-data"}
                                  [:div.col-sm-2
                                   [:label {:for "vidurl"} "Video URL"]]
                                  [:div.col-sm-7
                                   [:input.form-control {:type "text" :name "url" :id "vidurl" :placeholder "https://www.youtube.com/watch?v=ctrjYPPYlI0"}]]
                                  [:div.col-sm-1
                                   [:button.btn.btn-default {:type "submit"} "Add Video"]]]]
                                [:br]
                                [:div.row
                                 [:form {:method "POST" :action "/add" :enctype "multipart/form-data"}
                                  [:div.col-sm-2
                                   [:label {:for "file"} "MP3 File"]]
                                  [:div.col-sm-7
                                   [:input.form-control {:multiple "" :type "file" :name "file" :id "file" :placeholder "c:\\jj.mp3"}]]
                                  [:div.col-sm-1
                                   [:button.btn.btn-default {:type "submit"} "Add MP3 File"]]]]]]

                              [:br]
                              [:h2 "Podcasts"]
                              "Sort:&nbsp;&nbsp;" [:a.btn.btn-default.btn-xs {:href "/sort/ascending"} "Ascending"] "&nbsp;" [:a.btn.btn-default.btn-xs {:href "/sort/decending"} "Descending"]
                              [:br]
                              [:br]
                              [:table.table.table-striped
                               [:tr [:th "Name" [:th "Size"] [:th "Controls"]]]
                               (map list-file (get-podcast-files))]]))))))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).

(def session-interceptor (ring-mw/session {:store (cookie/cookie-store)}))

(def common-interceptors [(body-params/body-params) http/html-body  session-interceptor])

(defn delete-post [request]
  (let [fname-raw (get-in request [:path-params :file])
        fname-decode (java.net.URLDecoder/decode fname-raw "UTF-8")
        fname (.replaceAll fname-decode "/" "")
        file (io/file data-dir-file fname)]
        (println "fname" fname )
    (.delete file)
    (ring-resp/redirect "/")))

(defn flash [msg]
    (assoc (ring-resp/redirect "/") :session {:flash msg}))

(defn sort-handle [request]
  (let [dir (get-in request [:path-params :direction])
        files (.listFiles data-dir-file)
        ascend-comp  #(.compareTo %2 %1)
        descend-comp  #(.compareTo %1 %2)
        use-comp (if (= dir "ascending") ascend-comp descend-comp)
        sorted (sort-by #(.getName %) use-comp (.listFiles data-dir-file))
        mod-time (System/currentTimeMillis)]
    (doseq [x sorted]
      (let [j (+ mod-time (* 60000 (.indexOf sorted x)))]
        (.setLastModified x j)))
    (flash (str "Sorted " dir "!"))))

(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/rss" :get (conj common-interceptors `rss-page)]
              ["/add" :post (conj [(io.pedestal.http.ring-middlewares/multipart-params) session-interceptor `add-page])]
              ["/content/:content" :get (conj common-interceptors `content-page)]
              ["/delete/:file" :post (conj common-interceptors `delete-post)]
              ["/sort/:direction" :get (conj common-interceptors `sort-handle)]
              ["/about" :get (conj common-interceptors `about-page)]})

;; Consumed by podcastmaker.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 3000
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})

