(ns stylefy.impl.dom
  (:require [dommy.core :as dommy]
            [reagent.core :as r]
            [garden.core :refer [css]]
            [garden.stylesheet :refer [at-media at-keyframes at-font-face]])
  (:require-macros [reagent.ratom :refer [run!]]))

(def styles-in-use (r/atom {})) ;; style hash -> props
(def keyframes-in-use (r/atom []))
(def font-faces-in-use (r/atom []))
(def custom-classes-in-use (r/atom []))

(def ^:private stylefy-node-id :#_stylefy-styles_)
(def ^:private stylefy-constant-node-id :#_stylefy-constant-styles_)
(def ^:private dom-needs-update? (atom false))

(defn- style-by-hash [style-hash]
  (get @styles-in-use style-hash))

(defn- update-style-tags!
  [node node-constant]
  (let [styles-in-css (map (fn [style-hash]
                             (::css (style-by-hash style-hash)))
                           (keys @styles-in-use))
        keyframes-in-css (map (fn [keyframes]
                                (css keyframes))
                              @keyframes-in-use)
        font-faces-in-use (map (fn [properties]
                                 (css properties))
                               @font-faces-in-use)
        custom-classes-in-use (map (fn [class-definition]
                                     (css [(keyword (str "." (::class-name class-definition)))
                                           (::class-properties class-definition)]))
                                   @custom-classes-in-use)]
    (dommy/set-text! node-constant (apply str (concat font-faces-in-use
                                                      keyframes-in-css
                                                      custom-classes-in-use)))
    (dommy/set-text! node (apply str styles-in-css))))

(defn- mark-styles-added-in-dom! []
  (reset! styles-in-use (apply merge (map
                                       #(-> {% (assoc (get @styles-in-use %) ::in-dom? true)})
                                       (keys @styles-in-use)))))

(declare continuously-update-styles-in-dom!)

(defn- request-dom-update []
  (.requestAnimationFrame js/window continuously-update-styles-in-dom!))

(defn- update-styles-in-dom!
  "Updates style tag if needed."
  []
  (when @dom-needs-update?
    (let [node (dommy/sel1 stylefy-node-id)
          node-constant (dommy/sel1 stylefy-constant-node-id)]
      (if (and node node-constant)
        (do (update-style-tags! node node-constant)
            (reset! dom-needs-update? false)
            (mark-styles-added-in-dom!))
        (.error js/console "stylefy is unable to find the required <style> tags!")))))

(defn- continuously-update-styles-in-dom!
  "Updates style tag if needed."
  []
  (when @dom-needs-update?
    (update-styles-in-dom!))
  (request-dom-update))

(defn init-dom-update []
  (continuously-update-styles-in-dom!))

(defn- convert-props
  [{:keys [props hash] :as style} options]
  (let [general-style-props (apply dissoc props (filter namespace (keys props)))
        class-selector (keyword (str "." hash))
        garden-class-definition [class-selector general-style-props]
        stylefy-modes (:stylefy.core/mode props)
        garden-pseudo-classes (mapv #(-> [(keyword (str "&" %)) (% stylefy-modes)])
                                    (keys stylefy-modes))
        vendors (when-let [vendors (:stylefy.core/vendors props)]
                  {:vendors vendors
                   :auto-prefix (:stylefy.core/auto-prefix props)})
        css-class-options (or (merge options vendors) {})
        css-class (css css-class-options (into garden-class-definition garden-pseudo-classes))]
    css-class))

(defn- convert-media-queries
  [{:keys [props hash] :as style} options]
  (let [class-selector (keyword (str "." hash))
        stylefy-media-queries (:stylefy.core/media props)
        css-media-queries (map (fn [media-query]
                                 ;; TODO dissoc namespaced keywords, add support for vendor prefixes here?
                                 (css options (at-media media-query
                                                        [class-selector
                                                         (get stylefy-media-queries
                                                              media-query)])))
                               (keys stylefy-media-queries))]
    (apply str css-media-queries)))

(defn- convert-supports-rules
  [{:keys [props hash] :as style} options]
  (let [class-selector (keyword (str "." hash))
        stylefy-supports (:stylefy.core/supports props)
        css-supports (map (fn [supports-selector]
                            ;; TODO Make it possible to use @media inside @supports.
                            ;; TODO dissoc namespaced keywords, add support for vendor prefixes here?
                            (str "@supports (" supports-selector ") {"
                                 (css options [class-selector
                                               (get stylefy-supports
                                                    supports-selector)])
                                 "}"))
                          (keys stylefy-supports))]
    (apply str css-supports)))

(defn style->css
  ([style] (style->css style {}))
  ([{:keys [props hash] :as style} options]
   (let [css-class (convert-props style options)
         css-media-queries (convert-media-queries style options)
         css-supports (convert-supports-rules style options)]
     (str css-class css-media-queries css-supports))))

(defn- save-style!
  "Stores the style in an atom. The style is going to be added in DOM soon."
  [{:keys [props hash] :as style}]
  (assert props "Unable to save empty style!")
  (assert hash "Unable to save style without hash!")
  (let [style-css (style->css style)]
    (swap! styles-in-use assoc hash
           (assoc props ::css style-css))
    (reset! dom-needs-update? true)))

(defn style-in-dom? [style-hash]
  (boolean (::in-dom? (style-by-hash style-hash))))

(defn add-keyframes [identifier & frames]
  (let [garden-definition (apply at-keyframes identifier frames)]
    (swap! keyframes-in-use conj garden-definition)
    (reset! dom-needs-update? true)
    garden-definition))

(defn add-font-face [properties]
  (let [garden-definition (at-font-face properties)]
    (swap! font-faces-in-use conj garden-definition)
    (reset! dom-needs-update? true)
    garden-definition))

(defn add-class [name properties]
  (let [custom-class-definition {::class-name name ::class-properties properties}]
    (swap! custom-classes-in-use conj custom-class-definition)
    (reset! dom-needs-update? true)
    custom-class-definition))