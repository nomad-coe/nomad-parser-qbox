(function(){
'use strict';
angular.module('metaDataApp', ['ngRoute','metaDataApp.mainController','graphApp.graphController']).
config(['$routeProvider',
function($routeProvider) {
    $routeProvider.
    when('/:version/:metaInfoName', {
        templateUrl: 'views/metaInfo.html',
        controller: 'mainController'
    }).
    when('/graph', {
        templateUrl: 'views/multiGraph.html',
        controller: 'graphController'
    }).
    otherwise({
        redirectTo: '/common/section_single_configuration_calculation'
    });
}]);


})();