(ns org.tomhume.so.IdentitySO)
(import 'clojure.lang.Reflector)
(use 'org.tomhume.so.Bytecode)
(use 'org.tomhume.so.Opcodes)
(use 'org.tomhume.so.TestMap)

; Basic details of the class: class name, method name, method signature

(def class-name-root "IdentityTest31")
(def method-name "identity")
(def method-signature "(I)I")

; set up a map of equivalence tests

(defn invoke-method [class arg] (Reflector/invokeStaticMethod class method-name (into-array [arg])))

(defn one-is-one? [i] (= 1 (invoke-method i 1)))
(defn zero-is-zero? [i] (= 0 (invoke-method i 0)))
(defn minus-one-is-minus-one? [i] (= -1 (invoke-method i -1)))
(defn minint-is-minint? [i] (= Integer/MIN_VALUE (invoke-method i Integer/MIN_VALUE)))
(defn maxint-is-maxint? [i] (= Integer/MAX_VALUE (invoke-method i Integer/MAX_VALUE)))
(defn one-is-not-zero? [i] (not (= 1 (invoke-method i 0))))
(defn one-is-not-minus-one? [i] (not (= 1 (invoke-method i -1))))

;(def eq-tests-filter (test-map [one-is-one? zero-is-zero? minus-one-is-minus-one? minint-is-minint? maxint-is-maxint? one-is-not-zero? one-is-not-minus-one?]))
(def eq-tests-filter (test-map [zero-is-zero? one-is-one?]))

; generate all 2-sequence bytecodes
; map each one to a class file
; load the class file
; pass it through the equivalence test map

(filter #(try (do (println %) (passes? eq-tests-filter %)) (catch VerifyError e false)) (map-indexed (fn [idx code] (get-class (:code code)  (str class-name-root "-" idx) meth-name method-signature))
     (mapcat identity (map expand-opcodes (opcode-sequence 2))))) ; generate all 2-sequence bytecodes

; add equivalence tests, run for each class 
; unload all generated classes at the end, to avoid namespace feck-ups