;; Copyright (c) 2015 Jonathan L. Leonard

(ns speako.test.core
  (:require [cljs.test :refer-macros [deftest is async]]
            [cljs.nodejs :as node]
            [speako.common :refer [format]]
            [speako.core]))

(def gql (node/require "graphql"))

(defn- call-graphql
  ([cb schema query] (call-graphql cb schema query nil))
  ([cb schema query params]
    (.then ((.-graphql gql) schema query params) (fn [res]
      (cb (.stringify js/JSON res nil 2))))))

(defn file-schema [resolver] (speako.core/get-schema resolver "./resources/schema.gql"))

(defn str-schema
  ([schema-str] (str-schema {} schema-str))
  ([resolver schema-str] (speako.core/get-schema resolver schema-str)))

(deftest loads-schema-from-file (is (file-schema {})))

(deftest simple-select
  (async done
    (let [expected {"data" {"Colors" nil}}
          comparator (fn [s] (is (= expected (js->clj (.parse js/JSON s)))) (done))
          resolver {:query (fn [typename predicate]
                             (is (and (= typename "Color")
                                      (= predicate (js/JSON.stringify #js {"all" true})))) nil)}]
      (call-graphql comparator (file-schema resolver) "{ Colors { id name } }"))))

(deftest reserved-entities-forbidden
  (is (thrown-with-msg? js/Error #"Timestamp is a reserved entity provided by speako\."
                        (str-schema "type Timestamp { id: ID! }"))))

(deftest simple-timestamp
  (async
   done
   (let [expected [(js/Date.) (js/Date.)]
         comparator (fn [a]
                      (is (= (js->clj (.parse js/JSON a))
                             {"data" {"As" [{"ts" (map #(.toISOString %) expected)}]}})) (done))
         resolver {:query (fn [typename predicate]
                            (is (= typename "A"))
                            (clj->js [{:id "1" :ts expected}]))}]
     (call-graphql
      comparator
      (str-schema resolver "type A { id: ID! ts: [Timestamp]! }")
      "{ As { ts }}"))))

(deftest multiple-relations-of-same-type-with-reverse-link-forbidden
  (is (thrown-with-msg? js/Error #"Type 'A' involves duplicate \(bidirectional\) links to types: \(\"B\"\)."
                        (str-schema "type A { id: ID! b1: B b2: B } type B { id: ID! a: A }"))))
