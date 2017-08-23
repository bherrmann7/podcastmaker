(ns podcastmaker.layout)

(defn layout [pagename html]
  (let [base ""
        is-active (fn [a-pagename] (if (= pagename a-pagename) "class='active' " ""))]
    (str
     "<!DOCTYPE html>
<html lang='en'>
  <head>
    <meta charset='utf-8'>
    <meta http-equiv='X-UA-Compatible' content='IE=edge'>
    <meta name='viewport' content='width=device-width, initial-scale=1'>
    <link rel='icon' href='" base "favicon.ico'>
    <title>Podcast Maker</title>
    <link href='" base "/css/bootstrap.min.css' rel='stylesheet'>
    <style>
    body {
        min-height: 2000px;
        padding-top: 70px;

    }
    .alert-auto {display:inline-block;}
    </style>
  </head>
  <body>
    <nav class='navbar navbar-default navbar-fixed-top'>
      <div class='container'>
        <div class='navbar-header'>
          <button type='button' class='navbar-toggle collapsed' data-toggle='collapse' data-target='#navbar' aria-expanded='false' aria-controls='navbar'>
            <span class='sr-only'>Toggle navigation</span>
            <span class='icon-bar'></span>
            <span class='icon-bar'></span>
            <span class='icon-bar'></span>
          </button>
          <a class='navbar-brand' href='/'>Podcast Maker</a>
        </div>
        <div id='navbar' class='navbar-collapse collapse'>
          <ul class='nav navbar-nav'>
            <li " (is-active "Home") "><a href='" base "/'>Home</a></li>
            <li " (is-active "About") "><a href='" base "/about'>About</a></li>
          </ul>
         <ul class='nav navbar-nav navbar-right'>
              <li><a href='/logout'>Logout</a></li>
            </ul>
        </div>
      </div>
    </nav>
    <div class='container'>"
     html
     "</div>
    <script src='https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js'></script>
    <script>window.jQuery || document.write('<script src="
     "\""
     base "/js/jquery.min.js"
     "\""
     "><\\/script>')</script>
    <script src='" base "/js/bootstrap.min.js'></script>
  </body>
</html>")))
