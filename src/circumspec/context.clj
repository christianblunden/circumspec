(ns circumspec.context
  (:use [clojure.contrib.def :only (defvar defalias)]
        [clojure.contrib.str-utils :only (re-gsub)]
        [clojure.contrib.seq-utils :only (flatten)]
        [circumspec.should :only (reorder-form)]
        [circumspec.utils :only (resolve! defn!)]))

(defvar *context* ()
  "Stack of contexts")

(defn expand-subcontext-forms
  [desc forms]
  (when (seq forms)
    `(binding [*context* (conj *context* '~desc)]
       ~@forms)))

(defn dasherize [s]
  (re-gsub #"\s+" "-" s))

; TODO: does not work with macro names or ns prefixes
(defn test-function-name
  "Create a test function name. If the provided desc is a var,
   append suffix to prevent name collision between var and test.
   If desc is a human friendly string, dasherize it."
  [desc]
  (symbol (if (and (symbol? desc) (resolve desc))
            (str desc "-test")
            (dasherize (str desc)))))

(defn test-function-metadata
  [desc forms]
  (merge {:circumspec/spec true
          :circumspec/name desc
          :circumspec/context 'circumspec.context/*context*}
         (if (empty? forms)
           {:circumspec/pending true}
           {})))

(defmacro describe
  "Execute forms with desc pushed onto the spec context."
  [desc & forms]
  (let [desc (if (symbol? desc) (resolve! desc) desc)]
    `(do
       ~(expand-subcontext-forms desc forms))))

(defmacro it
  "Create a test function named after desc, recording
   the context in metadata"
  [desc & forms]
  `(defn! ~(with-meta (test-function-name desc) (test-function-metadata desc forms))
     "Generated test from the it macro."
     []
     ~@(map reorder-form forms)))

(defalias testing it)

(defn spec?
  "Does var refer to a spec?"
  [var]
  (assert (var? var))
  (boolean (:circumspec/spec (meta var))))

(defn spec-name
  "Name of a spec"
  [var]
  (assert (var? var))
  (:circumspec/name (meta var)))

(defn pending?
  "Is spec pending?"
  [var]
  (assert (var? var))
  (boolean (:circumspec/pending (meta var))))

(defn spec-description
  "Description of a spec (:context and :name)"
  [var]
  (assert (var? var))
  {:context (:circumspec/context (meta var))
   :name (:circumspec/name (meta var))})

(defn ns-vars
  [ns]
  (map second (ns-publics ns)))


(defmulti spec-vars #(if (sequential? %) :namespaces :namespace))

(defmethod spec-vars :namespaces
  [namespaces]
  (flatten
   (map spec-vars namespaces)))

(defmethod spec-vars :namespace
  [ns]
  (require ns)
  (filter spec? (ns-vars ns)))




