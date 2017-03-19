
;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.backend
  (:require [speako.schema :as schema]
            [loom.graph :as graph]
            [clojure.set]))

(defn- consume-types []
  (let [result (atom {:objects {} :unions {} :enums {}})]
    (reify schema/TypeConsumer
      (consume-object [_ typename fields]
        (swap! result assoc-in [:objects (keyword typename)] fields))
      (consume-union [_ typename constituent-types]
        (swap! result assoc-in [:unions (keyword typename)] (map keyword constituent-types)))
      (consume-enum [_ typename constituent-types]
        (swap! result assoc-in [:enums (keyword typename)] constituent-types))
      (finished [_] @result))))

(defn- object-edges [nodes [node fields]]
  (remove nil? (map (fn [[name type list? required?]]
                      (let [typ (keyword type)]
                        (when (nodes typ)
                          [node typ]))) fields)))

(defn- construct-graph [parsed]
  (let [nodes (set (keys (parsed :objects)))
        unions (set (keys (parsed :unions)))
        object-edges (partial object-edges (clojure.set/union nodes unions))
        edges (remove empty? (mapcat object-edges (parsed :objects)))]
    (apply graph/digraph (concat edges nodes))))
