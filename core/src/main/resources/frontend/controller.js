    angular.module('metaVisualizationApp', [])
    .controller('AllDataController', ['$scope', '$http', function($scope, $http) {

      $http.get('/nmi/v/last/info.json').success(function(data) {
       var parse = angular.fromJson(data);
       $scope.Data =  parse['metaInfos'];
       $scope.display($scope.Data[0]);		
     })

      $scope.display = function(data){
        $http.get('/nmi/v/last/n/' + data['name']+'/completeinfo.json').success(function(data) {
         $scope.displayComplete = angular.fromJson(data);
        })
      }
      $scope.displayName = function(data){
        $http.get('/nmi/v/last/n/' + data+'/completeinfo.json').success(function(data) {
         $scope.displayComplete = angular.fromJson(data);
        })
      }

    }]);

