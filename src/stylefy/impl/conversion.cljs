(ns stylefy.impl.conversion
  (:require [garden.core :refer [css]]
            [stylefy.impl.utils :as utils]
            [garden.stylesheet :refer [at-media at-keyframes at-font-face]]))

(defn- convert-stylefy-vendors-to-garden [props]
  (when-let [vendors (:stylefy.core/vendors props)]
    {:vendors vendors
     :auto-prefix (:stylefy.core/auto-prefix props)}))

(defn- convert-stylefy-modes-garden [props]
  (let [modes (:stylefy.core/mode props)]
    (mapv #(-> [(keyword (str "&" %)) (% modes)])
          (keys modes))))

(defn- convert-base-style
  [{:keys [props hash] :as style} options]
  (let [style-props (utils/filter-props props)
        class-selector (keyword (str "." hash))
        garden-class-definition [class-selector style-props]
        garden-pseudo-classes (convert-stylefy-modes-garden props)
        garden-vendors (convert-stylefy-vendors-to-garden props)
        garden-options (or (merge options garden-vendors) {})
        css-class (css garden-options (into garden-class-definition
                                            garden-pseudo-classes))]
    css-class))

(defn- convert-media-queries
  [{:keys [props hash] :as style} options]
  (when-let [stylefy-media-queries (:stylefy.core/media props)]
    (let [class-selector (keyword (str "." hash))
          css-media-queries
          (map
            (fn [media-query]
              (let [media-query-props (get stylefy-media-queries media-query)
                    style-props (utils/filter-props media-query-props)
                    garden-class-definition [class-selector style-props]
                    garden-pseudo-classes (convert-stylefy-modes-garden media-query-props)
                    garden-vendors (convert-stylefy-vendors-to-garden media-query-props)
                    garden-options (or (merge options garden-vendors) {})]
                (css garden-options (at-media media-query (into garden-class-definition
                                                                garden-pseudo-classes)))))
            (keys stylefy-media-queries))]
      (apply str css-media-queries))))

(defn- convert-supports-rules
  [{:keys [props hash] :as style} options]
  (when-let [stylefy-supports (:stylefy.core/supports props)]
    (let [class-selector (keyword (str "." hash))
          css-supports (map
                         (fn [supports-selector]
                           (let [supports-props (get stylefy-supports supports-selector)
                                 style-props (utils/filter-props supports-props)
                                 garden-class-definition [class-selector style-props]
                                 garden-pseudo-classes (convert-stylefy-modes-garden style-props)
                                 garden-vendors (convert-stylefy-vendors-to-garden supports-props)
                                 garden-options (or (merge options garden-vendors) {})
                                 css-media-queries-inside-supports
                                 (convert-media-queries
                                   {:props supports-props :hash hash}
                                   options)]
                             (str "@supports (" supports-selector ") {"
                                  (css garden-options (into garden-class-definition
                                                            garden-pseudo-classes))
                                  css-media-queries-inside-supports
                                  "}")))
                         (keys stylefy-supports))]
      (apply str css-supports))))

(defn style->css
  "Converts the given style to CSS. Options are sent directly to Garden"
  ([style] (style->css style {}))
  ([{:keys [props hash] :as style} options]
   (let [css-class (convert-base-style style options)
         css-media-queries (convert-media-queries style options)
         css-supports (convert-supports-rules style options)]
     (str css-class css-media-queries css-supports))))