;; Copyright (c) 2015 Jonathan L. Leonard

(ns speako.runner
  (:require [cljs.test :as test]
            [doo.runner :refer-macros [doo-all-tests doo-tests]]
            [speako.test.core]))

(doo-tests 'speako.test.core)

