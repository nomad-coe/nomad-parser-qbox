(function(){
        'use strict';
    angular.module('mathJaxDirective', [])
    .directive("mathjaxBind", function() {
        return {
            restrict: "A",
            scope:{
                text: "@mathjaxBind"
            },
            controller: ["$scope", "$element", "$attrs", function($scope, $element, $attrs) {
                $scope.$watch('text', function(value) {
                    var $script = angular.element("<script type='math/tex'>")
                        .html(value == undefined ? "" : value);
                    $element.html("");
                    $element.append($script);
                    MathJax.Hub.Queue(["Reprocess", MathJax.Hub, $element[0]]);
                });
            }]
        };
    })
    .directive('dynamic', function ($compile) {
      return {
        restrict: 'A',
        replace: true,
        link: function (scope, ele, attrs) {
          scope.$watch(attrs.dynamic, function(html) {
//          console.log(html)
          //Replace all the required mathjax styles here with the span. Currently supported \[ ... \], $$ ... $$ and $ ... $
          html = html.replace(/\$([^$]+)\$/g, "<span mathjax-bind=\"$1\"></span>");
          html = html.replace(/\$\$([^$]+)\$\$/g, "<span  mathjax-bind=\"$1\"></span>");
          html = html.replace(/\\\[([^$]+)\\\]/g, "<span mathjax-bind=\"$1\"></span>");
//          console.log(html)
          ele.html(html);
          $compile(ele.contents())(scope);
          });
        }
      };
    });

}());