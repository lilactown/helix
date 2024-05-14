(ns helix.server.hooks)


(defn use-state
  [initial]
  [initial (fn [& args])])


(defn use-ref
  [initial]
  (atom initial))


(defmacro use-effect [deps & body])

(defmacro use-layout-effect [deps & body])

(defmacro use-memo
  [deps & body]
  ~@body)

(defmacro use-callback
  [deps f]
  ~f)
