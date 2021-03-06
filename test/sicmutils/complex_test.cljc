;
; Copyright © 2017 Colin Smith.
; This work is based on the Scmutils system of MIT/GNU Scheme:
; Copyright © 2002 Massachusetts Institute of Technology
;
; This is free software;  you can redistribute it and/or modify
; it under the terms of the GNU General Public License as published by
; the Free Software Foundation; either version 3 of the License, or (at
; your option) any later version.
;
; This software is distributed in the hope that it will be useful, but
; WITHOUT ANY WARRANTY; without even the implied warranty of
; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
; General Public License for more details.
;
; You should have received a copy of the GNU General Public License
; along with this code; if not, see <http://www.gnu.org/licenses/>.
;

(ns sicmutils.complex-test
  (:require [clojure.test :refer [is deftest testing]]
            #?(:cljs [cljs.reader :refer [read-string]])
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]
             #?@(:cljs [:include-macros true])]
            [same :refer [ish? with-comparator]
             #?@(:cljs [:include-macros true])]
            [sicmutils.numbers]
            [sicmutils.complex :as c]
            [sicmutils.generators :as sg]
            [sicmutils.generic :as g]
            [sicmutils.generic-test :as gt]
            [sicmutils.generators :as sg]
            [sicmutils.laws :as l]
            [sicmutils.value :as v]))

(defn ^:private near [w z]
  (< (g/abs (g/- w z)) 1e-10))

(deftest complex-literal
  (testing "parse-complex can round-trip Complex instances. These show up as
  code snippets when you call `read-string` directly, and aren't evaluated into
  Clojure. The fork in the test here captures the different behavior that will
  appear in evaluated Clojure, vs self-hosted Clojurescript."
    (is (= #?(:clj  '(sicmutils.complex/complex 1.0 2.0)
              :cljs '(sicmutils.complex/complex "1 + 2i"))
           (read-string {:readers {'sicm/complex c/parse-complex}}
                        (pr-str #sicm/complex "1 + 2i"))))))

(deftest complex-laws
  ;; Complex numbers form a field. We use a custom comparator to control some
  ;; precision loss.
  (with-comparator (v/within 1e-3)
    (l/field 100 sg/complex "Complex")))

(deftest value-protocol
  (testing "v/Value protocol implementation"
    (is (v/zero? c/ZERO))
    (is (v/zero? #sicm/complex "0"))
    (is (not (v/zero? c/ONE)))
    (is (not (v/zero? (c/complex 1.0))))
    (is (v/zero? (v/zero-like (c/complex 100))))
    (is (= c/ZERO (v/zero-like (c/complex 2))))
    (is (= c/ZERO (v/zero-like #sicm/complex "0 + 3.14i")))

    (is (v/one? c/ONE))
    (is (v/one? (c/complex 1.0)))
    (is (v/one? (v/one-like c/ZERO)))
    (is (not (v/one? (c/complex 2))))
    (is (not (v/one? (c/complex 0.0))))

    (is (= 10.0 (v/freeze (c/complex 10)))
        "If the imaginary piece is 0, freeze will return only the real part.")
    (is (v/numerical? (c/complex 10)))

    (testing "exact?"
      (is (not (v/exact? (c/complex 0 10.1))))

      ;; cljs is able to maintain exact numbers here.
      #?@(:clj
          [(is (not (v/exact? (c/complex 10))))
           (is (not (v/exact? (c/complex 10 12))))]

          :cljs
          [(is (v/exact? (c/complex 10)))
           (is (v/exact? (c/complex 10 12)))]))))

(let [pi Math/PI]
  (deftest complex-numbers
    (testing "complex constructor and predicate"
      (is (c/complex? c/ONE))
      (is (c/complex? c/I))
      (is (c/complex? #sicm/complex "2"))
      (is (not (c/complex? 4))))

    (testing "complex-generics"
      (let [skip #{:quotient :gcd :remainder :modulo :negative? :exact-divide}]
        (gt/integral-tests c/complex :exclusions skip :eq near)
        (gt/floating-point-tests c/complex :eq near)))

    (testing "add"
      (is (= #sicm/complex "4 + 6i"
             (g/add #sicm/complex "1 + 2i"
                    #sicm/complex "3 + 4i")))
      (is (= (c/complex 1 3) (g/add (c/complex 0 3) 1)))
      (is (= (c/complex 1 3)
             (g/add 1 (c/complex 0 3))
             (g/add (c/complex 0 3) 1))))

    (testing "sub"
      (is (= (c/complex -2 -2) (g/sub (c/complex 1 2)
                                      (c/complex 3 4))))
      (is (= (c/complex 10 2) (g/sub (c/complex 20 2) 10)))
      (is (= (g/negate (c/complex 10 2))
             (g/sub 10 (c/complex 20 2)))))

    (testing "mul between numbers and complex numbers in both orders"
      ;; rotate 7 by pi/2
      (is (near (g/mul c/I 7) (g/mul 7 (g/exp (g/mul c/I (/ pi 2))))))
      (is (near (c/complex 0 7) (g/mul (c/complex 7) (g/exp (g/mul c/I (/ pi 2)))))))

    (testing "div in either order"
      (is (= (c/complex 0 -1) (g/div 1 c/I)))
      (is (= (c/complex 2 2) (g/div (c/complex 4 4) 2))))

    (testing "expt"
      (is (near -1 (g/expt (c/complex 0 1) 2)))
      (is (near (c/complex 16) (g/expt 2 (c/complex 4))))
      (is (near (c/complex 16) (g/expt (c/complex 2) (c/complex 4)))))

    (testing "negate"
      (is (= (c/complex -10 2)
             (g/negate (c/complex 10 -2)))))

    (testing "invert"
      (is (v/zero? (g/add c/I (g/invert c/I)))))

    (testing "abs"
      (is (= 5.0 (g/abs (c/complex 3 4)))))

    (testing "exp"
      ;; Euler identity
      (is (near (c/complex -1) (g/exp (g/mul c/I pi)))))

    (testing "log"
      (is (= (g/mul c/I pi) (g/log (g/exp (g/mul c/I pi))))))

    (testing "square"
      (is (near (g/mul c/I 200) (g/square (c/complex 10 10)))))

    (testing "cube"
      (is (near (c/complex 0 -8) (g/cube (g/* 2 c/I))))
      (is (near (c/complex 27) (g/cube (c/complex 3))))
      (is (near (c/complex -27) (g/cube (c/complex -3)))))

    (testing "sqrt"
      (is (near (c/complex 10 10) (g/sqrt (g/mul c/I 200)))))

    (testing "arithmetic"
      (is (v/numerical? c/I)))))

(deftest trig-tests
  (testing "sin"
    (is (near (g/sin (c/complex 10))
              (Math/sin 10))))

  (testing "cos"
    (is (near (g/cos (c/complex 10))
              (Math/cos 10))))

  (testing "tan"
    (is (near (g/tan (c/complex 10))
              (Math/tan 10))))

  (testing "asin"
    (is (near (g/asin (c/complex 1.1))
              (c/complex 1.57079632679489 -0.443568254385115))))

  (testing "acos"
    (is (near (g/acos (c/complex 1.1))
              (c/complex 0 0.4435682543851153))))

  (testing "atan"
    (is (near (g/atan (c/complex 1.1))
              (c/complex 0.8329812666744317 0.0))))

  (let [z (c/complex 3 4)]
    (testing "cot"
      (is (near (g/invert (g/tan z))
                (g/cot z))))

    (testing "cosh"
      (is (near (c/complex -6.5806630406 -7.5815527427)
                (g/cosh z))))

    (testing "sinh"
      (is (near (c/complex -6.5481200409 -7.6192317203)
                (g/sinh z))))

    (testing "tanh"
      (is (near (g/div (g/sinh z) (g/cosh z))
                (g/tanh z))))

    (testing "sec"
      (is (near (g/invert (g/cos z))
                (g/sec z))))

    (testing "csc"
      (is (near (g/invert (g/sin z))
                (g/csc z))))

    (testing "sech"
      (is (near (g/invert (g/cosh z))
                (g/sech z))))

    (testing "acosh"
      (is (near z (g/cosh (g/acosh z)))))

    (testing "asinh"
      (is (near z (g/sinh (g/asinh z)))))

    (testing "atanh"
      (is (near z (g/tanh (g/atanh z)))))))

(deftest promotions-from-real
  (is (= (c/complex 0 1) (g/sqrt -1)))
  (is (near (c/complex 1.57079632679489 -0.443568254385115) (g/asin 1.1)))
  (is (near (c/complex 0 0.4435682543851153) (g/acos 1.1)))
  (is (near (c/complex 0 Math/PI) (g/log -1))))

(deftest extra-functions
  (testing "functions needed for docs"
    (is (near (g/real-part (c/complex 3 4)) 3))
    (is (near (g/imag-part (c/complex 3 4)) 4))
    (is (near (g/imag-part (g/conjugate (c/complex 3 4))) -4))
    (is (near (g/magnitude (c/complex 0 1)) 1))
    (is (near (g/magnitude (c/complex 1 0)) 1))
    (is (near (g/magnitude (c/complex 1 1)) (g/sqrt 2))))

  (checking "transpose, determinant act as id" 100 [z sg/complex]
            (is (= z (g/transpose z)))
            (is (= z (g/determinant z))))

  (checking "conjugate/magnitude" 100 [z sg/complex]
            (is (ish? (g/magnitude z)
                      (g/real-part
                       (g/sqrt
                        (g/* z (g/conjugate z)))))))

  (checking "real/imag-part" 100 [z sg/complex]
            (is (= (g/negate (g/imag-part z))
                   (g/imag-part (g/conjugate z))))

            (is (= (g/real-part z)
                   (g/real-part (g/conjugate z))))

            (is (= z (g/+ (g/real-part z)
                          (g/* #sicm/complex "0+1i"
                               (g/imag-part z))))))

  (checking "angle" 100 [z sg/complex]
            (is (near (g/angle z)
                      (g/atan
                       (g/imag-part z)
                       (g/real-part z))))
            (let [rt (g/* (g/magnitude z)
                          (g/exp (g/* #sicm/complex "0+1i"
                                      (g/angle z))))]
              (with-comparator (v/within 1e-8)
                (is (ish? (g/real-part z)
                          (g/real-part rt)))
                (is (ish? (g/imag-part z)
                          (g/imag-part rt)))))))
