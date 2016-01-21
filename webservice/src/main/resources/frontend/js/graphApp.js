(function(){
'use strict';
angular.module('graphApp', ['ngRoute','graphApp.graphController']).
config(['$routeProvider',
function($routeProvider) {
    $routeProvider.
    when('/graph', {
        templateUrl: 'views/multiGraph.html',
        controller: 'graphController'
    }).
    otherwise({
        redirectTo: '/graph'
    });
}]);


})();