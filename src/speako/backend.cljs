
;; Copyright (c) 2016 Jonathan L. Leonard

(ns speako.backend
  (:require [speako.schema :as schema]
            [loom.graph :as graph]
            [loom.attr :as attr]
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
                          [node typ list? required?]))) fields)))

(defn- construct-graph [parsed]
  (let [nodes (set (keys (parsed :objects)))
        unions (set (keys (parsed :unions)))
        object-edges (partial object-edges (clojure.set/union nodes unions))
        edges (remove empty? (mapcat object-edges (parsed :objects)))
        g (apply graph/digraph (concat (map (partial take 2) edges) nodes))
        attrs (mapcat #(let [edge (take 2 %1)] [[edge :list? (%1 2)] [edge :required? (%1 3)]]) edges)]
    (reduce #(attr/add-attr-to-edges %1 (%2 1) (%2 2) [(%2 0)]) g attrs)))
