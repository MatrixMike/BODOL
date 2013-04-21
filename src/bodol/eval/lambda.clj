(ns bodol.eval.lambda
  (:require [bodol.eval.core :as eval]
            [bodol.lambda :as l]
            [bodol.types :as t])
  (:import [bodol.types LCons]))



(defn invoke [func args]
  (fn [outer-scope]
    (let [arity (l/-arity func)
          scope (l/-scope func)
          curried-args (l/-curried-args func)
          [args outer-scope] (eval/map-eval outer-scope args)
          args (concat curried-args args)
          call-arity (count args)]
      (cond
       (> call-arity arity)
       (throw (ex-info (str "function of arity " arity " invoked with "
                            call-arity " arguments " (t/pr-value args))
                       {:args args :func func}))

       (< call-arity arity)
       [(l/curry func args) outer-scope]

       :else
       (if-let [match (l/pattern-match func args)]
         (let [[clause scope-mv] match
               scope (-> (scope-mv scope)
                         second
                         (merge {:function func}))
               [result final-scope]
               ((eval/eval (:body clause)) scope)]
           [result outer-scope])
         (throw (ex-info
                 (str "function call did not match any defined patterns "
                      "(" (t/pr-value func) " "
                      (clojure.string/join " " (map t/pr-value args)) ")")
                 {:args args :scope outer-scope :function func})))))))

(extend-type LCons
  eval/Eval
  (eval/-eval [this]
    (fn [scope]
      (let [[func & args] (seq this)
            [func scope] ((eval/-eval func) scope)]
        (cond
         (fn? func) ((func args) scope)

         (l/lambda? func)
         (let [[result scope] ((invoke func args) scope)]
           [result scope])

         :else (throw (ex-info (str "invoking non-function " func)
                               {:value this :scope scope})))))))
