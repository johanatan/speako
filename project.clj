(defproject speako "0.1.0-SNAPSHOT"
  :description "GraphQL Schema Language Compiler"
  :url "https://github.com/johanatan/speako"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [instaparse "1.4.4"]]
  :node-dependencies [[source-map-support "0.2.8"]
                      [graphql "0.8.2"]]
  :plugins [[lein-npm "0.6.2"]
            [lein-cljsbuild "1.1.4"]]
  :clean-targets ["out" "target"]
  :cljsbuild {
    :builds {
      :main {
        :source-paths ["src"]
        :compiler {
          :output-to "out/prod/speako.js"
          :output-dir "out/prod"
          :optimizations :simple
          :target :nodejs}}}})
