(ns psyclyx.malli.amalloy.ring-buffer-readme-test
  (:require
    [amalloy.ring-buffer :as rb]
    #?(:clj [clojure.test :refer [deftest is testing]]
       :cljs [cljs.test :refer [deftest is testing]])
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


;; Formatting is kinda strange in this file to faciliate easier copy-pasting

(deftest validation
  (is
    (m/validate [:amalloy/ring-buffer :int]
                (into (rb/ring-buffer 3) [1 2 3]))) ; => true
  (is (not
        (m/validate [:amalloy/ring-buffer :int]
                    [1 2 3])) ; => false
      )
  (testing "Capacity"
    (testing "Exact"
      (is
        (m/validate [:amalloy/ring-buffer {:capacity 5} :int]
                    (rb/ring-buffer 5))  ; => true
        )
      (is (not
            (m/validate [:amalloy/ring-buffer {:capacity 5} :int]
                        (rb/ring-buffer 10))) ; => false
          ))
    (testing "Minimum bound"
      (is
        (m/validate [:amalloy/ring-buffer {:min-capacity 5} :int]
                    (rb/ring-buffer 5)))  ; => true
      (is (not
            (m/validate [:amalloy/ring-buffer {:min-capacity 5} :int]
                        (rb/ring-buffer 3)) ; => false
            )))
    (testing "Maximum bound"
      (is
        (m/validate [:amalloy/ring-buffer {:max-capacity 5} :int]
                    (rb/ring-buffer 3)) ; => true
        )
      (is (not
            (m/validate [:amalloy/ring-buffer {:max-capacity 5} :int]
                        (rb/ring-buffer 10)) ; => false
            )))))


(deftest error-messages
  (is (= ["should be a ring buffer"]
         (->> [1 2 3]
              (m/explain [:amalloy/ring-buffer :int])
              me/humanize) ; => ["should be a ring buffer"]
         ))
  (is (= ["should have capacity 5"]
         (->> (into (rb/ring-buffer 3) [1 2 3])
              (m/explain [:amalloy/ring-buffer {:capacity 5} :int])
              me/humanize) ; => ["should have capacity 5"]
         ))
  (is (= ["should have capacity >= 4"]
         (->> (into (rb/ring-buffer 3) [1 2 3])
              (m/explain [:amalloy/ring-buffer {:min-capacity 4} :int])
              me/humanize) ; => ["should have capacity >= 4"]
         ))
  (is (= ["should have capacity between 1 and 2"]
         (->> (into (rb/ring-buffer 3) [1 2 3])
              (m/explain [:amalloy/ring-buffer {:min-capacity 1 :max-capacity 2} :int])
              me/humanize) ; => ["should have capacity between 1 and 2"]
         ))
  (is (= [["should be a string"] ["should be a string"] ["should be a string"]]
         (->> (into (rb/ring-buffer 3) [1 2 3])
              (m/explain [:amalloy/ring-buffer :string])
              (me/humanize)) ; => [["should be a string"] ["should be a string"] ["should be a string"]]
         )))


(deftest generation
  (is (m/validate [:amalloy/ring-buffer :int]
                  (mg/generate [:amalloy/ring-buffer :int]) ; => #amalloy/ring-buffer [59 (-32326 -19097 1154648 94 -1 -67 -72204790)]
                  ))
  (is (m/validate [:amalloy/ring-buffer {:capacity 3} :int]
                  (mg/generate [:amalloy/ring-buffer {:capacity 3} :int]) ; => #amalloy/ring-buffer [3 (3838072 503345 7160549)]
                  ))
  (is (m/validate [:amalloy/ring-buffer {:min-capacity 3} :int]
                  (mg/generate [:amalloy/ring-buffer {:min-capacity 3} :int]) ; => #amalloy/ring-buffer [11 (1707179 -1 217 -3 -814 -71 644 -2 -561 0 118606)]
                  )))


(deftest transformation
  (testing "Encoding"
    (is (= [1 2 3]
           (m/encode [:amalloy/ring-buffer :int]
                     (into (rb/ring-buffer 3) [1 2 3])
                     (malli.rb/ring-buffer-transformer)) ; => [1 2 3]
           )))
  (testing "Decoding"
    (is (= (into (rb/ring-buffer 3) [1 2 3])
           (m/decode [:amalloy/ring-buffer :int]
                     [1 2 3]
                     (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [3 (1 2 3)]
           ))
    (testing "Capacity inference"
      (is (= (into (rb/ring-buffer 5) [1 2 3 4 5])
             (m/decode [:amalloy/ring-buffer {:min-capacity 3} :int]
                       [1 2 3 4 5]
                       (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [5 (1 2 3 4 5)]
             ))
      (testing "Underflow"
        (is (= (into (rb/ring-buffer 3) [1 2])
               (m/decode [:amalloy/ring-buffer {:min-capacity 3} :int]
                         [1 2]
                         (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [3 (1 2)]
               ))
        (is (= (into (rb/ring-buffer 3) [1 2])
               (m/decode [:amalloy/ring-buffer {:capacity 3} :int]
                         [1 2]
                         (malli.rb/ring-buffer-transformer)) ; => #amalloy/ring-buffer [3 (1 2)]
               )))
      (testing "Overflow"
        (is (= (range 5)
               (m/decode [:amalloy/ring-buffer {:capacity 3} :int]
                         (range 5)
                         (malli.rb/ring-buffer-transformer)) ; => (0 1 2 3 4)
               ))
        (is (= (range 5)
               (m/decode [:amalloy/ring-buffer {:max-capacity 3} :int]
                         (range 5)
                         (malli.rb/ring-buffer-transformer)) ; => (0 1 2 3 4)
               ))
        (is (= (into (rb/ring-buffer 3) [2 3 4])
               (m/decode [:amalloy/ring-buffer {:max-capacity 3} :int]
                         (range 5)
                         (malli.rb/ring-buffer-transformer {:overflow true})) ; => #amalloy/ring-buffer [3 (2 3 4)]
               ))))
    (testing "Nested transformations"
      (is (= {"xs" ["1" "2" "3"]}
             (m/encode
               [:map [:foo/xs [:amalloy/ring-buffer :int]]]
               {:foo/xs (into (rb/ring-buffer 5) [1 2 3])}
               (mt/transformer
                 (mt/key-transformer {:encode name})
                 (malli.rb/ring-buffer-transformer)
                 (mt/string-transformer))) ; => {"xs" ["1" "2" "3"]}
             ))
      (is (= {:foo/xs (into (rb/ring-buffer 5) [1 2 3])}
             (m/decode
               [:map [:foo/xs [:amalloy/ring-buffer {:capacity 5} :int]]]
               {"xs" ["1" "2" "3"]}
               (mt/transformer
                 (mt/key-transformer {:decode {"xs" :foo/xs}})
                 (malli.rb/ring-buffer-transformer)
                 (mt/string-transformer))) ; => {:foo/xs #amalloy/ring-buffer [5 (1 2 3)]}
             )))))
