(function(){
'use strict';
angular.module('metaDataApp', ['ngRoute','metaDataApp.mainController']).
config(['$routeProvider',
function($routeProvider) {
    $routeProvider.
    when('/:version/:metaInfoName', {
        templateUrl: 'views/metaInfo.html',
        controller: 'mainController'
    }).
    otherwise({
        redirectTo: '/common/section_single_configuration_calculation'
    });
}]);


})();