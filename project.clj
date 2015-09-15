(defproject graphql-tlc "0.1.0-SNAPSHOT"
  :description "GraphQL Type Language Compiler"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.lucasbradstreet/instaparse-cljs "1.4.1.0"]
                 [org.clojure/clojurescript "0.0-3308"]]
  :node-dependencies [[source-map-support "0.2.8"]
                      [zmq "2.11.1"]
                      [q "1.4.1"]
                      [express "4.13.3"]
                      [body-parser "1.13.2"]
                      [morgan "1.6.1"]
                      [passport "0.2.2"]
                      [passport-http-bearer "1.0.1"]
                      [sha1 "1.1.1"]
                      [graphql "0.4.2"]]
  :plugins [[lein-npm "0.4.0"]
            [lein-doo "0.1.5-SNAPSHOT"]
            [lein-cljsbuild "1.0.5"]]
  :hooks [leiningen.cljsbuild]
  :source-paths ["src" "target/classes"]
  :clean-targets ["out"]
  :cljsbuild {
    :builds {
      :test {
        :source-paths ["src" "test"]
        :compiler {
          :output-to "out/test/graphql-tlc/graphql-tlc.js"
          :output-dir "out/test/graphql-tlc"
          :main 'graphql-tlc.runner
          :optimizations :simple
          :pretty-print true
          :target :nodejs
          :hashbang false
          :cache-analysis true
          :source-map "out/test/graphql-tlc/graphql-tlc.js.map"}}
      :main {
        :source-paths ["src"]
        :compiler {
          :output-to "out/graphql-tlc/graphql-tlc.js"
          :output-dir "out/graphql-tlc"
          :optimizations :simple
          :pretty-print true
          :target :nodejs
          :hashbang false
          :cache-analysis true
          :source-map "out/graphql-tlc/graphql-tlc.js.map"}}}
    :test-commands {:unit ["node" :node-runner "out/graphql-tlc/graphql-tlc.js"]}})

