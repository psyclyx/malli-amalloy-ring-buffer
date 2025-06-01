(ns psyclyx.malli.amalloy.ring-buffer
  "Malli schema implementation for amalloy.ring-buffer"
  (:require
    [amalloy.ring-buffer :as rb]
    [clojure.test.check.generators :as gen]
    [malli.core :as m]
    [malli.generator :as mg]
    [malli.transform :as mt])
  #?(:clj
     (:import
       (amalloy.ring_buffer
         RingBuffer)
       (java.lang
         Integer))))


#?(:clj
   (defn ring-buffer?
     [x]
     (instance? RingBuffer x))

   :cljs
   (defn ring-buffer?
     [x]
     (instance? rb/RingBuffer x)))


(defn -capacity
  [rb]
  (count (.-buf rb)))


(defn -validate-capacity
  [rb {:keys [capacity min-capacity max-capacity]}]
  (cond
    capacity
    (= capacity (-capacity rb))

    (or min-capacity max-capacity)
    (<= (or min-capacity 0)
        (-capacity rb)
        (or max-capacity
            #?(:clj Integer/MAX_VALUE
               :cljs js/Number.MAX_SAFE_INTEGER)))

    :else true))


(defn -assert-capacity-properties
  [{:keys [capacity max-capacity min-capacity] :as properties}]
  (when (and capacity (or max-capacity min-capacity))
    (throw (ex-info "Cannot specify :capacity and :min-capacity/:max-capacity"
                    {:schema :ring-buffer
                     :properties properties}))))


(defn -capacity-error-fragment-en
  [{:keys [capacity min-capacity max-capacity]}]
  (cond
    capacity
    (str "capacity " capacity)

    (and min-capacity max-capacity)
    (str "capacity between " min-capacity " and " max-capacity)

    min-capacity
    (str "capacity >= " min-capacity)

    max-capacity
    (str "capacity <= " max-capacity)))


(defn -error-message-en
  [{:keys [schema value]} _options]
  (let [properties (m/properties schema)
        capacity-fragment (-capacity-error-fragment-en properties)]
    (cond
      (not (ring-buffer? value))
      "should be a ring buffer"

      (not (-validate-capacity value properties))
      (str "should have " capacity-fragment)

      :else
      (if capacity-fragment
        (str "should be a ring buffer with " capacity-fragment)
        "should be a ring buffer"))))


(defn -validator
  [properties]
  (fn valid?
    [?rb]
    (boolean
      (and (ring-buffer? ?rb)
           (-validate-capacity ?rb properties)))))


(def -ring-buffer-into-schema
  (m/-collection-schema
    {:compile
     (fn [{:keys [capacity] :as properties} _children _options]
       (-assert-capacity-properties properties)
       {:type :ring-buffer
        :type-properties {:error/fn {:en -error-message-en}}
        :pred (-validator properties)
        :empty (some-> capacity rb/ring-buffer)})}))


(defn -sequential->rb-fn
  [{:keys [overflow schema]}]
  (let [properties (m/properties schema)]
    (fn [xs]
      (if-not (sequential? xs)
        xs
        (let [capacity (or (:capacity properties) (count xs))
              rb (rb/ring-buffer capacity)
              [first-xs overflow-xs] (split-at capacity xs)]
          (cond
            ;; may have inferred capacity by counting xs
            (not (-validate-capacity rb properties)) xs
            (not (seq overflow-xs)) (into rb first-xs)
            ;; only consume `xs` once
            overflow (into rb (concat first-xs overflow-xs))
            :else xs))))))


(defn ring-buffer-transformer
  ([] (ring-buffer-transformer {}))
  ([{:keys [decode-overflow]}]
   (mt/transformer
     {:decoders
      {:ring-buffer
       {:compile (fn [schema _options]
                   (-sequential->rb-fn {:overflow decode-overflow
                                        :schema schema}))}}
      :encoders
      {:ring-buffer #(some-> % vec)}})))


(defmethod mg/-schema-generator :ring-buffer [schema options]
  (let [[child-schema] (m/-children schema)
        properties (m/properties schema)
        child-gen (mg/generator child-schema options)
        {:keys [capacity min-capacity max-capacity]
         gen-min :gen/min
         gen-max :gen/max} properties
        cap-min (or capacity min-capacity 0)
        cap-max (or capacity max-capacity 100)
        size-min (or gen-min 0)
        size-max (or gen-max cap-max)]
    (gen/fmap
      (fn [[cap elements]]
        (into (rb/ring-buffer cap) (take cap elements)))
      (gen/tuple
        (gen/choose cap-min cap-max)
        (gen/vector child-gen size-min size-max)))))


(def registry
  {:ring-buffer -ring-buffer-into-schema})
