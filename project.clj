(defproject speako "0.10.35"
  :description "GraphQL Schema Language Compiler"
  :url "https://github.com/johanatan/speako"
  :license "none"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [sqlingvo.node "0.1.0"]
                 [aysylu/loom "1.0.0"]
                 [funcool/promesa "1.8.0"]
                 [camel-snake-kebab "0.4.0"]
                 [instaparse "1.4.4"]]
  :npm {:dependencies [[source-map-support "0.2.8"]
                       [pg "5.0.0"]
                       [pg-native "1.10.0"]
                       [pluralize "4.0.0"]
                       [graphql-union-input-type "0.2.2"]
                       [graphql "0.8.2"]]}
  :plugins [[lein-npm "0.6.2"]
            [lein-doo "0.1.7"]
            [lein-cljsbuild "1.1.4"]]
  :clean-targets ["out" "target"]
  :cljsbuild
  {:builds
   {:main
    {:source-paths ["src"]
     :compiler
     {:output-to "out/prod/speako.js"
      :output-dir "out/prod"
      :optimizations :simple
      :target :nodejs}}
    :test
    {:source-paths ["src" "test"]
     :compiler
     {:output-to "out/test/speako.js"
      :output-dir "out/test"
      :optimizations :none
      :target :nodejs
      :main speako.runner}}}})
