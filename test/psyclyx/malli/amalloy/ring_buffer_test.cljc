(ns psyclyx.malli.amalloy.ring-buffer-test
  (:require
    [amalloy.ring-buffer :as rb]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as str]
    #?(:clj [clojure.test :refer [deftest testing is]]
       :cljs [cljs.test :refer [deftest testing is]])
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.properties :as prop]
    [malli.core :as m]
    [malli.error :as me]
    [malli.generator :as mg]
    [malli.registry :as mr]
    [malli.transform :as mt]
    [psyclyx.malli.amalloy.ring-buffer :as malli.rb]))


;; Setup registry for tests
(mr/set-default-registry!
  (mr/composite-registry
    (m/default-schemas)
    malli.rb/registry))


(deftest ring-buffer-validation-test
  (testing "Basic validation"
    (let [schema [:amalloy/ring-buffer :int]]
      (is (m/validate schema (rb/ring-buffer 5)))
      (is (m/validate schema (into (rb/ring-buffer 5) [1 2 3])))
      (is (not (m/validate schema [1 2 3])))
      (is (not (m/validate schema nil)))
      (is (not (m/validate schema "not a ring buffer")))))

  (testing "Element validation"
    (let [schema [:amalloy/ring-buffer :string]]
      (is (m/validate schema (into (rb/ring-buffer 3) ["a" "b" "c"])))
      (is (not (m/validate schema (into (rb/ring-buffer 3) [1 2 3]))))))

  (testing "Capacity constraints"
    (testing "Fixed capacity"
      (let [schema [:amalloy/ring-buffer {:capacity 5} :int]]
        (is (m/validate schema (rb/ring-buffer 5)))
        (is (not (m/validate schema (rb/ring-buffer 3))))
        (is (not (m/validate schema (rb/ring-buffer 10))))))

    (testing "Capacity range"
      (let [schema [:amalloy/ring-buffer {:min-capacity 3 :max-capacity 10} :int]]
        (is (m/validate schema (rb/ring-buffer 3)))
        (is (m/validate schema (rb/ring-buffer 5)))
        (is (m/validate schema (rb/ring-buffer 10)))
        (is (not (m/validate schema (rb/ring-buffer 2))))
        (is (not (m/validate schema (rb/ring-buffer 11)))))))

  (testing "Complex element schemas"
    (let [schema [:amalloy/ring-buffer [:map [:id :uuid] [:name :string]]]]
      (is (m/validate schema
                      (into (rb/ring-buffer 2)
                            [{:id (random-uuid) :name "Alice"}
                             {:id (random-uuid) :name "Bob"}])))
      (is (not (m/validate schema
                           (into (rb/ring-buffer 2)
                                 [{:id "not-uuid" :name "Alice"}])))))))


(deftest ring-buffer-error-messages-test
  (testing "Basic error messages"
    (is (= ["should be a ring buffer"]
           (-> (m/explain [:amalloy/ring-buffer :int] [1 2 3])
               me/humanize)))
    (is (= ["should be a ring buffer"]
           (-> (m/explain [:amalloy/ring-buffer :int] "not a ring buffer")
               me/humanize))))

  (testing "Capacity error messages"
    (is (= ["should have capacity 5"]
           (-> (m/explain [:amalloy/ring-buffer {:capacity 5} :int]
                          (rb/ring-buffer 3))
               me/humanize)))
    (is (= ["should have capacity >= 3"]
           (-> (m/explain [:amalloy/ring-buffer {:min-capacity 3} :int]
                          (rb/ring-buffer 2))
               me/humanize)))
    (is (= ["should have capacity <= 10"]
           (-> (m/explain [:amalloy/ring-buffer {:max-capacity 10} :int]
                          (rb/ring-buffer 11))
               me/humanize)))
    (is (= ["should have capacity between 3 and 10"]
           (-> (m/explain [:amalloy/ring-buffer {:min-capacity 3 :max-capacity 10} :int]
                          (rb/ring-buffer 2))
               me/humanize))))

  (testing "Element validation errors"
    (is (= [nil ["should be an integer"] ["should be an integer"]]
           (-> (m/explain [:amalloy/ring-buffer :int]
                          (into (rb/ring-buffer 3) [1 "two" "three"]))
               me/humanize)))))


(deftest ring-buffer-transformation-test
  (testing "Decode from sequential"
    (let [schema [:amalloy/ring-buffer :int]
          transformer (malli.rb/ring-buffer-transformer)]
      (is (= (into (rb/ring-buffer 3) [1 2 3])
             (m/decode schema [1 2 3] transformer)))
      ;; Empty sequence
      (is (= (rb/ring-buffer 0)
             (m/decode schema [] transformer)))
      ;; Non-sequential passes through
      (is (= "not sequential"
             (m/decode schema "not sequential" transformer)))))

  (testing "Decode with capacity constraints"
    (let [schema [:amalloy/ring-buffer {:capacity 3} :int]
          transformer (malli.rb/ring-buffer-transformer)]
      (is (= (into (rb/ring-buffer 3) [1 2 3])
             (m/decode schema [1 2 3] transformer)))
      ;; Shorter sequence still creates capacity-3 buffer
      (is (= (into (rb/ring-buffer 3) [1 2])
             (m/decode schema [1 2] transformer)))
      ;; Overflow truncated by default
      (is (= [1 2 3 4 5]
             (m/decode schema [1 2 3 4 5] transformer)))))

  (testing "Decode with overflow handling"
    (let [schema [:amalloy/ring-buffer {:capacity 3} :int]
          transformer (malli.rb/ring-buffer-transformer {:decode-overflow true})]
      ;; Overflow wraps around
      (is (= (into (rb/ring-buffer 3) [1 2 3 4 5])
             (m/decode schema [1 2 3 4 5] transformer)))))

  (testing "Encode to vector"
    (let [schema [:amalloy/ring-buffer :string]
          transformer (malli.rb/ring-buffer-transformer)
          rb (into (rb/ring-buffer 3) ["a" "b" "c"])]
      (is (= ["a" "b" "c"]
             (m/encode schema rb transformer)))))

  (testing "Round-trip transformation"
    (let [schema [:amalloy/ring-buffer {:capacity 5} :keyword]
          transformer (malli.rb/ring-buffer-transformer)
          data [:a :b :c]]
      (is (= data
             (as-> data %
                   (m/decode schema % transformer)
                   (m/encode schema % transformer))))))

  (testing "Composed with other transformers"
    (let [schema [:amalloy/ring-buffer [:string {:decode/string str/upper-case}]]
          transformer (mt/transformer
                        (malli.rb/ring-buffer-transformer)
                        mt/string-transformer)]
      (is (= (into (rb/ring-buffer 2) ["HELLO" "WORLD"])
             (m/decode schema ["hello" "world"] transformer))))))


(deftest ring-buffer-parsing-test
  (testing "Parse valid ring buffer"
    (let [schema [:amalloy/ring-buffer {:capacity 3} :int]
          rb (into (rb/ring-buffer 3) [1 2 3])]
      (is (= rb (m/parse schema rb)))
      (is (= rb (m/unparse schema rb)))))

  (testing "Parse returns nil for invalid"
    (let [schema [:amalloy/ring-buffer :int]]
      (is (= ::m/invalid (m/parse schema [1 2 3])))
      (is (= ::m/invalid (m/parse schema "not a ring buffer")))
      (is (= ::m/invalid (m/parse schema nil))))))


(deftest ring-buffer-generation-test
  (testing "Basic generation"
    (let [schema [:amalloy/ring-buffer :int]]
      (is (malli.rb/ring-buffer? (mg/generate schema)))
      ;; Check multiple samples are all ring buffers
      (is (every? malli.rb/ring-buffer? (mg/sample schema {:size 10})))))

  (testing "Generation with capacity"
    (let [schema [:amalloy/ring-buffer {:capacity 5} :int]]
      (dotimes [_ 10]
        (let [generated (mg/generate schema)]
          (is (= 5 (malli.rb/-capacity generated)))))))

  (testing "Generation with capacity range"
    (let [schema [:amalloy/ring-buffer {:min-capacity 3 :max-capacity 7} :int]]
      (dotimes [_ 10]
        (let [generated (mg/generate schema)
              capacity (malli.rb/-capacity generated)]
          (is (<= 3 capacity 7))))))

  (testing "Generation size constraints"
    (let [schema [:amalloy/ring-buffer {:capacity 10 :gen/min 5 :gen/max 10} :int]]
      (dotimes [_ 10]
        (let [generated (mg/generate schema)]
          (is (<= 5 (count generated) 10))))))

  (testing "Generation of complex elements"
    (let [schema [:amalloy/ring-buffer {:capacity 3}
                  [:map
                   [:id :uuid]
                   [:timestamp inst?]]]
          sample (mg/sample schema {:size 5})]
      (is (every? malli.rb/ring-buffer? sample))
      (is (every? #(every? map? %) sample)))))


(deftest ring-buffer-property-validation-test
  (testing "Invalid property combinations"
    (is (thrown? #?(:clj Exception :cljs js/Error)
          (m/schema [:amalloy/ring-buffer {:capacity 5 :min-capacity 3} :int])))
    (is (thrown? #?(:clj Exception :cljs js/Error)
          (m/schema [:amalloy/ring-buffer {:capacity 5 :max-capacity 10} :int])))))


(deftest ring-buffer-edge-cases-test
  (testing "Empty ring buffer"
    (let [schema [:amalloy/ring-buffer :any]]
      (is (m/validate schema (rb/ring-buffer 5)))
      (is (= [] (m/encode schema (rb/ring-buffer 5)
                          (malli.rb/ring-buffer-transformer))))))

  (testing "Zero capacity"
    (let [schema [:amalloy/ring-buffer {:capacity 0} :int]]
      (is (m/validate schema (rb/ring-buffer 0)))
      (is (= (rb/ring-buffer 0)
             (m/decode schema [] (malli.rb/ring-buffer-transformer))))))

  (testing "Large capacity"
    (let [schema [:amalloy/ring-buffer {:min-capacity 1000} :int]
          rb (rb/ring-buffer 1000)]
      (is (m/validate schema rb))))

  (testing "Ring buffer at capacity"
    (let [schema [:amalloy/ring-buffer {:capacity 3} :int]
          rb (into (rb/ring-buffer 3) [1 2 3])]
      (is (m/validate schema rb))
      ;; Add one more element to check wraparound
      (let [rb-wrapped (conj rb 4)]
        (is (m/validate schema rb-wrapped))
        (is (= [2 3 4] (vec rb-wrapped)))))))


(deftest integration-test
  (testing "Full pipeline: generate -> validate -> transform -> encode"
    (let [schema [:amalloy/ring-buffer {:capacity 5 :gen/min 3}
                  [:map [:x :int] [:y :string]]]
          transformer (malli.rb/ring-buffer-transformer)]
      (let [generated (mg/generate schema)]
        (is (m/validate schema generated))
        (let [encoded (m/encode schema generated transformer)]
          (is (vector? encoded))
          (is (<= 3 (count encoded) 5))
          (let [decoded (m/decode schema encoded transformer)]
            (is (malli.rb/ring-buffer? decoded))
            (is (= (vec generated) (vec decoded)))))))))


(defspec ring-buffer-roundtrip-property
  100
  (prop/for-all [capacity (gen/choose 1 20)
                 element-type (gen/elements [:int :string :keyword :boolean])]
                (let [schema [:amalloy/ring-buffer {:capacity capacity} element-type]
                      transformer (malli.rb/ring-buffer-transformer)]
                  (when-let [rb (mg/generate schema)]
                    (let [encoded (m/encode schema rb transformer)
                          decoded (m/decode schema encoded transformer)]
                      (and
                        ;; Encoding produces a vector
                        (vector? encoded)
                        ;; Preserves element count
                        (= (count rb) (count encoded))
                        ;; Decoding produces a ring buffer
                        (malli.rb/ring-buffer? decoded)
                        ;; Capacity is preserved
                        (= capacity (malli.rb/-capacity decoded))
                        ;; Elements are preserved
                        (= (vec rb) (vec decoded))))))))


(defspec ring-buffer-validation-property
  100
  (prop/for-all [capacity (gen/choose 1 50)
                 elements (gen/vector (gen/int) 0 100)]
                (let [schema [:amalloy/ring-buffer {:capacity capacity} :int]
                      rb (into (rb/ring-buffer capacity) elements)]
                  (and
                    ;; Valid ring buffers validate
                    (m/validate schema rb)
                    ;; Wrong capacity fails validation
                    (not (m/validate [:amalloy/ring-buffer {:capacity (inc capacity)} :int] rb))
                    ;; Plain vectors don't validate
                    (not (m/validate schema elements))))))


(deftest ring-buffer-wraparound-validation-test
  (testing "Validation works correctly after wraparound"
    (let [schema [:amalloy/ring-buffer {:capacity 3} pos-int?]
          rb (into (rb/ring-buffer 3) [1 2 3 4 5])]  ; [3 4 5]
      (is (m/validate schema rb))
      ;; Invalid element after wraparound
      (let [rb-invalid (conj rb -1)]  ; [4 5 -1]
        (is (not (m/validate schema rb-invalid)))))))


(deftest transformer-nil-handling-test
  (testing "Transformer handles nil values"
    (let [schema [:amalloy/ring-buffer :any]
          transformer (malli.rb/ring-buffer-transformer)]
      (is (nil? (m/decode schema nil transformer)))
      (is (nil? (m/encode schema nil transformer))))))


(defspec element-validation-consistency
  50
  (prop/for-all [valid-elements (gen/vector (gen/gen-for-pred pos-int?)
                                            1 10)
                 invalid-elements (gen/vector (gen/gen-for-pred neg-int?)
                                              1 10)]
                (let [schema [:amalloy/ring-buffer pos-int?]
                      rb-valid (into (rb/ring-buffer 20) valid-elements)
                      rb-invalid (into (rb/ring-buffer 20) invalid-elements)]
                  (and (m/validate schema rb-valid)
                       (not (m/validate schema rb-invalid))))))


(deftest nested-ring-buffer-test
  (testing "Ring buffer of ring buffers"
    (let [schema [:amalloy/ring-buffer {:capacity 3} [:amalloy/ring-buffer :int]]
          inner1 (into (rb/ring-buffer 2) [1 2])
          inner2 (into (rb/ring-buffer 2) [3 4])
          outer (into (rb/ring-buffer 3) [inner1 inner2])]
      (is (m/validate schema outer)))))


(deftest pick-capacity-test
  (testing "Exact capacity"
    (is (= 5 (malli.rb/-pick-capacity {:capacity 5} (range 0))))
    (is (= 5 (malli.rb/-pick-capacity {:capacity 5} (range 3))))
    (is (= 5 (malli.rb/-pick-capacity {:capacity 5} (range 5))))
    (is (= 5 (malli.rb/-pick-capacity {:capacity 5} (range 10)))))
  (testing "Capacity inference"
    (testing "Maximum bound"
      (is (= 0 (malli.rb/-pick-capacity {:max-capacity 5} (range 0))))
      (is (= 3 (malli.rb/-pick-capacity {:max-capacity 5} (range 3))))
      (is (= 5 (malli.rb/-pick-capacity {:max-capacity 5} (range 5))))
      (is (= 5 (malli.rb/-pick-capacity {:max-capacity 5} (range 10)))))

    (testing "Minimum bound"
      (is (= 5 (malli.rb/-pick-capacity {:min-capacity 5} (range 0))))
      (is (= 5 (malli.rb/-pick-capacity {:min-capacity 5} (range 3))))
      (is (= 6 (malli.rb/-pick-capacity {:min-capacity 5} (range 6)))))

    (testing "Maximum and minimum bounds"
      (is (= 5 (malli.rb/-pick-capacity {:min-capacity 5 :max-capacity 10}
                                        (range 0))))
      (is (= 5 (malli.rb/-pick-capacity {:min-capacity 5 :max-capacity 10}
                                        (range 3))))
      (is (= 5 (malli.rb/-pick-capacity {:min-capacity 5 :max-capacity 10}
                                        (range 5))))
      (is (= 6 (malli.rb/-pick-capacity {:min-capacity 5 :max-capacity 10}
                                        (range 6))))
      (is (= 10 (malli.rb/-pick-capacity {:min-capacity 5 :max-capacity 10}
                                         (range 10))))
      (is (= 10 (malli.rb/-pick-capacity {:min-capacity 5 :max-capacity 10}
                                         (range 11)))))))
