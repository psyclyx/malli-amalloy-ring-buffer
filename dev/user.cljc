(ns user
  (:require
    [amalloy.ring-buffer :as rb]
    [malli.core :as m]
    [malli.error :as me]
    [malli.generator :as mg]
    [malli.registry :as mr]
    [malli.transform :as mt]
    [psyclyx.malli.amalloy.ring-buffer :as malli.rb]))


(mr/set-default-registry!
  (mr/composite-registry
    (m/default-schemas)
    malli.rb/registry))


;; Basic validation

(def schema [:amalloy/ring-buffer :int])

(m/validate schema (rb/ring-buffer 3)) ; true

(m/validate schema (into (rb/ring-buffer 3) [1 2 3])) ; true

(m/validate schema [1 2 3]) ; false

;; Capacity constraints

(def capacity-schema [:amalloy/ring-buffer {:capacity 5} :string])

(m/validate capacity-schema (into (rb/ring-buffer 5) ["a" "b"])) ; true

(m/validate capacity-schema (into (rb/ring-buffer 10) ["a" "b"])) ; false

(def range-schema [:amalloy/ring-buffer {:min-capacity 3 :max-capacity 10} :keyword])

(m/validate range-schema (into (rb/ring-buffer 5) [:a :b :c])) ; true

(m/validate range-schema (into (rb/ring-buffer 15) [:a :b :c :d])) ; false

;; Generation

(mg/generate schema)

(mg/generate [:amalloy/ring-buffer {:capacity 3 :gen/min 2 :gen/max 3} :string])


;; Transformation

(m/decode capacity-schema ["hello" "world"]
          mt/collection-transformer) ; #amalloy/ring-buffer [5 ("hello" "world")]

;; Error messages

(-> (m/explain schema [1 2 3]) (me/humanize)) ; ["should be a ring buffer"]


(-> (m/explain [:amalloy/ring-buffer :int] (into (rb/ring-buffer 3) ["a" "b"]))
    (me/humanize)) ; ["should be an integer" "should be an integer"]


;; Complex schemas

(def user-buffer
  [:amalloy/ring-buffer {:capacity 16}
   [:map
    [:id :uuid]
    [:name :string]
    [:created inst?]]])


(mg/sample user-buffer {:size 3})
