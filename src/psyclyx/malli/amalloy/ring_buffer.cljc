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


(defn ring-buffer?
  "Check if `x` is a ring buffer."
  [x]
  (instance? #?(:clj RingBuffer
                :cljs rb/RingBuffer)
             x))


(defn -capacity
  "Capacity of `rb`, a ring buffer."
  [rb]
  (count (.-buf rb)))


(defn -validate-capacity
  "Validate `rb`'s capacity against `:amalloy/ring-buffer` schema properties"
  [rb {:keys [capacity min-capacity max-capacity] :as _properties}]
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
  "Throw an exception if `:amalloy/ring-buffer` schema properties
  include both exact and range capacity contraints."
  ;; AFAICT, -property-schema is supposed to return a :map schema at
  ;; the top-level, which I don't think can encode this constraint.
  ;; Also, -collection-schema doesn't accept a property schema anyways,
  ;; so we would need to write an entire IntoSchema implementation.
  [{:keys [capacity max-capacity min-capacity] :as properties}]
  (when (and capacity (or max-capacity min-capacity))
    ;; This happens at schema `:compile`-time, so I think we should
    ;; just throw an ex-info instead of m/-fail? Probably fine?
    (throw (ex-info "Cannot specify :capacity and :min-capacity/:max-capacity"
                    {:schema :amalloy/ring-buffer
                     :properties properties}))))


(defn- -capacity-error-fragment-en
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


(defn- -error-message-en
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
  "Construct a validation predicate for a `:amalloy/ring-buffer`
  schema properties map."
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
       {:type :amalloy/ring-buffer
        :type-properties {:error/fn {:en -error-message-en}}
        :pred (-validator properties)
        :empty (some-> capacity rb/ring-buffer)})}))


(defn -pick-capacity
  [{:keys [capacity max-capacity min-capacity]} xs]
  (or capacity
      (cond-> (count xs)
        max-capacity (min max-capacity)
        min-capacity (max min-capacity))))


(defn -sequential->ring-buffer-fn
  "Construct a function that decodes sequentials to ring buffers.

  Options:
    - `:overflow` - decode sequences larger than capacity instead of passing through"
  [properties {:keys [overflow]}]
  (fn [xs]
    (if-not (sequential? xs)
      xs
      (let [capacity (-pick-capacity properties xs)
            rb (rb/ring-buffer capacity)
            will-overflow? (seq (drop capacity xs))]
        (cond
          (not (-validate-capacity rb properties)) xs
          (or overflow (not will-overflow?)) (into rb xs)
          :else xs)))))


(defn ring-buffer-transformer
  "Transforms between ring buffers and sequentials.

  Optionally accepts `opts`:
    - `:overflow` - decode sequences larger than capacity instead of passing through"
  ([] (ring-buffer-transformer {}))
  ([opts]
   (mt/transformer
     {:decoders
      {:amalloy/ring-buffer
       {:compile (fn [schema _options]
                   {:leave (-sequential->ring-buffer-fn (m/properties schema) opts)})}}
      :encoders
      {:amalloy/ring-buffer {:enter #(some-> % vec)}}})))


(defmethod mg/-schema-generator :amalloy/ring-buffer [schema options]
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
  {:amalloy/ring-buffer -ring-buffer-into-schema})
