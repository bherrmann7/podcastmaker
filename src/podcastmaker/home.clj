(ns podcastmaker.home
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

(use 'hiccup.core)

  (defn list-file [file]
    (vec
     [:tr
      [:td [:a {:href (str "/content/" (.getName file))} (.getName file)]]
      [:td (format "%,d" (.length file))]
      [:td [:form {:method "POST" :action (str "/delete/" (.getName file))}
            [:input {:type "submit" :value "Delete"}]]]]))


  (defn home-page
    [request]
    (let [host (get-in request [:headers "host"])
          rss-feed (str "http://" host "/rss/" (ud/id request))]
      (ud/flash-scrub request (ring-resp/response
                               (h/layout "Home"
                                         (html
                                          (if-not (nil? (get-in request [:session :flash]))
                                            [:div.alert.alert-success (get-in request [:session :flash])])
                                          [:div
                                           [:div.panel.panel-default
                                            [:div.panel-body
                                             "Your Podcast Subcription/Feed is  "
                                             [:a {:href rss-feed} rss-feed]]]

                                           [:div.panel.panel-default
                                            [:div.panel-heading
                                             [:h3.panel-title "Add Audio Content"]]
                                            [:div.panel-body
                                             #_[:div.row
                                              [:form {:method "POST" :action "/add" :enctype "multipart/form-data"}
                                               [:div.col-sm-2
                                                [:label {:for "vidurl"} "Video URL"]]
                                               [:div.col-sm-7
                                                [:input.form-control {:type "text" :name "url" :id "vidurl" :placeholder "https://www.youtube.com/watch?v=ctrjYPPYlI0"}]]
                                               [:div.col-sm-1
                                                [:button.btn.btn-default {:type "submit"} "Add Video"]]]]
                                             #_[:br]
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
                                            (map list-file (ud/get-podcast-files (ud/id request)))]]))))))
