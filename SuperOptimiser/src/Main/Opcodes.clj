(ns Main.Opcodes)
(use 'clojure.set)
(use 'clojure.test)
(use 'clojure.math.combinatorics)
(use 'Main.Global)
(use '[Filters.RedundancyFilter :only (no-redundancy?)])
(use '[Filters.InfluenceFilter :only (retains-influence?)])
(use '[Filters.OperandStackFilter :only (uses-operand-stack-ok?)])
(use '[Filters.VariableUseFilter :only (uses-vars-ok?)])
(use '[Filters.ReturnFilter :only (finishes-ireturn? no-ireturn?)])


; A list of opcodes which store into a variable. We count these so that
; we can derive a ceiling for the possible number of local variables.
(def storage-opcodes '[:istore :istore_0 :istore_1 :istore_2 :istore_3])

; Also any operation that takes 2 entries, calculates a result and is followed by a pop is redundant; could be replaced by a pop2
; and 2 constant-pushing operations followed by a pop2

(def redundant-pairs '(
                        [:swap :swap]       ; Two swaps leave things as they were
                        [:pop :pop]         ; Could be replaced by :pop2
                        [:ineg :ineg]       ; Two negations get us back where we started
                        [:iconst_0 :idiv]   ; Divide by zero, never fun
                        [:iconst_0 :irem]   ; Divide by zero, never fun
                        [:iconst_0 :pop]
                        [:iconst_m1 :pop]
                        [:iconst_1 :pop]
                        [:iconst_2 :pop]
                        [:iconst_3 :pop]
                        [:iconst_4 :pop]
                        [:iconst_5 :pop]
                        [:bipush :pop]
                        ))

(defn contains-no-redundant-pairs?
  "Does the supplied sequence contain any sequences of operations which are redundant?"
  [l]
  (loop [pairs redundant-pairs]
    (if (empty? pairs) true
      (do
        (let [cur-pair (first pairs) idx-first (.indexOf l (first cur-pair)) idx-next (inc idx-first)]
        (if
          (and
            (> idx-first -1)
            (< idx-first (dec (count l)))
            (= (second cur-pair) (nth l idx-next))) false
          (recur (rest pairs))))))))

(is (= false (contains-no-redundant-pairs? '[:ixor :swap :swap])))
(is (= false (contains-no-redundant-pairs? '[:swap :swap])))
(is (= false (contains-no-redundant-pairs? '[:swap :swap :ixor])))
(is (= true (contains-no-redundant-pairs? '[:ixor :swap :ixor :swap])))

(defn is-valid?
  "Master validity filter: returns true if this opcode sequence can form the basis of a viable bytecode sequence"
  [n s]
  (and
    (finishes-ireturn? s)
    (uses-vars-ok? n s)
    (uses-operand-stack-ok? s)
    (contains-no-redundant-pairs? s)
    (retains-influence? n s)
    (no-redundancy? n s)
))

(defn is-fertile?
  "Master fertility filter: returns true if any children of this opcode sequence s with n arguments may be valid"
  [n s]
  (and
    (no-ireturn? s)
    (uses-vars-ok? n s)
    (uses-operand-stack-ok? s)
    (contains-no-redundant-pairs? s)
    (no-redundancy? n s)
))

(defn get-children [n s] (if (or (empty? s) (is-fertile? n s)) (map #(conj s %) (keys opcodes))))

(defn opcode-sequence
  "Return a sequence of potentially valid opcode sequences N opcodes in length"
  [max-depth num-args]
  (let [validity-filter (partial is-valid? num-args) fertile-children (partial get-children num-args)]
    (filter validity-filter (rest (tree-seq #(< (count %) max-depth) fertile-children '[])))))

(defn count-storage-ops
  "Count the number of operations writing to a local variable in the supplied sequence"
  [s]
  (count (filter #(some #{%} storage-opcodes) s)))

(is (= 0 (count-storage-ops [:ixor :iushr])))
(is (= 1 (count-storage-ops [:ixor :istore])))
(is (= 2 (count-storage-ops [:ixor :istore :istore])))
(is (= 2 (count-storage-ops [:ixor :istore_0 :istore])))
(is (= 2 (count-storage-ops [:ixor :istore_0 :istore :ixor])))

(defn expand-arg
  "Returns a sequence of bytes appropriate for the keyword passed in and number of local variables"
  [vars k]
  (cond 
    (= k :local-var) (range 0 vars)
    (= k :s-byte) (range -127 128)
    (= k :us-byte) (range 0 256)
    (= k :byte) (range 0 256)
    :else (seq [k])))

(is (= '(0 1 2 3 4) (expand-arg 5 :local-var)))
(is (= nil) (expand-arg 1 :dummy-keyword))

(defn expand-opcodes
  "Take a sequence of opcodes s and expand the variables within it, returning all possibilities, presuming m arguments"
  [m s]
  (let [seq-length (count s) max-vars (+ m (count-storage-ops s))]
    
    (map #(hash-map :length seq-length :vars max-vars :code % )
              (apply cartesian-product
                (map (partial expand-arg max-vars) 
                     (flatten (map #(cons % (:args (opcodes %))) s)))))))

(defn expanded-numbered-opcode-sequence
  "Return a numbered, expanded sequence of all valid opcode permutations of length n presuming m arguments"
  [n m]
  (map-indexed (fn [idx itm] (assoc itm :seq-num idx))
               (mapcat identity
                       (map (partial expand-opcodes m) (opcode-sequence n m)))))

