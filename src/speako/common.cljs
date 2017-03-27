;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.common
  (:require [goog.string.format]
            [cljs.pprint :as pprint]
            [clojure.string :as string]
            [cljs.nodejs :as node]
            [camel-snake-kebab.core :refer [->kebab-case ->PascalCase]]))

(enable-console-print!)

(def fs (node/require "fs"))
(def pluralizer (node/require "pluralize"))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply goog.string/format fmt args))

(def DEBUG (atom false))
(defn pprint-str [obj] (pprint/write obj :stream nil))
(defn dbg-print [fmt & args] (if @DEBUG
                               (let [ppargs (map #(pprint-str %) args)]
                                 (js/console.log (apply (partial format fmt) ppargs)))))
(defn dbg-banner-print [fmt & args]
  (let [banner (string/join (repeat 85 "="))]
    (dbg-print (string/join [banner "\n" fmt "\n" banner]) args)))
(defn dbg-obj-print [obj] (js/console.log obj) obj)
(defn dbg-obj-print-in [props obj] (js/console.log (apply (partial aget obj) props)) obj)
(defn dbg-file [msg] ;; GraphQL eats console output occuring in our callbacks.
  (.appendFileSync fs "./debug.log" (format "%s\n" (pprint-str msg))) msg)

(defn jskeys [jsobj]
  (.keys js/Object jsobj))

(defn single [col] (assert (= 1 (count col))) (first col))

(defn pluralize [noun]
  (let [kebabbed (->kebab-case noun)
        splitted (string/split kebabbed #"-")
        transforms (concat (repeat (- (count splitted) 1) identity) [pluralizer])
        zipped (map vector splitted transforms)
        transformed (map #((%1 1) (%1 0)) zipped)
        joined (string/join "-" transformed)
        pluralized (->PascalCase joined)]
    (if (= pluralized noun)
      (format "%ses" pluralized) ; unconventional handling of uncountables; tack on "es"
      pluralized)))
