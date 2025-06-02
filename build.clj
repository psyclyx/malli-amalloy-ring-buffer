(ns build
  (:require
    [clojure.string :as str]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as dd]))


(defn- pr-depth
  [opts & xs]
  (print (apply str (repeat (::depth opts 0) "  ")))
  (apply println xs)
  opts)


(defn- inc-depth
  [opts]
  (update opts ::depth (fnil inc 0)))


(defn- sanitize-branch-name
  [branch-name]
  (-> branch-name
      (str/replace #"/" "-")
      (str/replace #"[^a-zA-Z0-9\-_.]" "_")
      (str/trim)))


(def lib 'xyz.psyclyx/malli-amalloy-ring-buffer)
(def git-revs (b/git-count-revs nil))


(def git-branch
  (some-> (b/git-process {:git-args ["branch" "--show-current"]})
          (sanitize-branch-name)))


(def version
  (format "0.1.%s%s"
          git-revs
          (if-not (= "main" git-branch)
            (str "-" git-branch)
            "")))


(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))


(def basis (delay (b/create-basis {:project "deps.edn"})))


(defn tests
  "Run all the tests."
  [opts]
  (pr-depth opts "Running tests...")
  (newline)
  (pr-depth opts "--------v(test)v--------\n")
  (let [basis (b/create-basis {:project "deps.edn"
                               :aliases [:libs :test]})
        cmds (b/java-command {:basis basis
                              :main 'clojure.main
                              :main-args ["-m" "kaocha.runner" "--reporter" "documentation"]})]
    (when-not (zero? (:exit (b/process cmds)))
      (throw (ex-info "Tests failed" {})))
    (newline)
    (pr-depth opts "--------^(test)^--------\n")
    opts))


(defn- pom-template
  [version]
  [[:description "Malli schemas for clj-commons/ring-buffer"]
   [:url "https://github.com/psyclyx/malli-amalloy-ring-buffer"]
   [:licenses
    [:license
     [:name "MIT"]
     [:url "https://mit-license.org"]]]
   [:developers
    [:developer
     [:name "psyclyx"]]]
   [:scm
    [:url "https://github.com/psyclyx/malli-amalloy-ring-buffer"]
    [:connection "scm:git:https://github.com/psyclyx/malli-amalloy-ring-buffer.git"]
    [:developerConnection "scm:git:ssh:git@github.com:psyclyx/malli-amalloy-ring-buffer.git"]
    [:tag (str "v" version)]]])


(defn- jar-opts
  [opts]
  (assoc opts
         ::depth 0
         :class-dir class-dir
         :lib lib
         :version version
         :basis @basis
         :src-dirs ["src"]
         :target-dir class-dir
         :jar-file jar-file
         :pom-data (pom-template version)))


(defn clean
  "Clean old build files."
  [opts]
  (pr-depth opts "Cleaning...")
  (b/delete {:path "target"})
  opts)


(defn- pom
  "Write pom.xml."
  [opts]
  (-> opts
      (pr-depth "Writing pom.xml...")
      b/write-pom)
  opts)


(defn- copy-dir
  "Copy files from `:src-dirs` to `:target-dir`."
  [opts]
  (-> opts
      (pr-depth "Copying files...")
      b/copy-dir)
  opts)


(defn jar
  "Build jar."
  [opts]
  (-> opts
      jar-opts
      (pr-depth "Building jar...")
      inc-depth
      pom
      copy-dir
      b/jar)
  opts)


(defn ci
  "Clean, test, and build a jar."
  [opts]
  (-> opts
      jar-opts
      (pr-depth "Running CI build...")
      inc-depth
      clean
      tests
      jar))


(defn install
  "Install jar to local maven repository."
  [opts]
  (-> opts
      (pr-depth "Installing jar to local maven repository...")
      b/install)
  opts)


(defn deploy
  "Deploy the jar to Clojars."
  [opts]
  (println "Deploying to Clojars...")
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote
                :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
