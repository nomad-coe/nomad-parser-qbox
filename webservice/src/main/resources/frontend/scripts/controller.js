    angular.module('metaVisualizationApp', [])
    .controller('AllDataController', ['$scope', '$http', function($scope, $http) {
      $scope.dataSearchList = [];

      //TODO:Replace arborjs with cytoscapejs
      //TODO: Convert angular to Jquery
      var sys = arbor.ParticleSystem(1000, 400,1);
      sys.parameters({gravity:false});
      sys.renderer = Renderer("#viewport") ;

      $http.get('/nmi/v/last/info.json').success(function(data) {
       var parse = angular.fromJson(data);
       $scope.metaDataList =  parse['metaInfos'];
       $scope.display($scope.metaDataList[0]['name']);    
     })

      $scope.addToList = function(str){
        console.log("Adding a new element niow")
        if ($scope.dataSearchList.indexOf(str) >= 0) {
          var index = $scope.dataSearchList.indexOf(str);
          if (index > -1) {
           $scope.dataSearchList.splice(index, 1);
          }
        }
        else {
          $scope.dataSearchList.push(str); 
        }
      }
      $scope.display = function(data){
        $http.get('/nmi/v/last/n/' + data+'/annotated.json').success(function(resData) {
         $scope.dataToDisplay = angular.fromJson(resData);

          $http.get('/nmi/v/last/n/' + data+'/allparents.json').success(function(allparentsData) {
            sys.prune();
            sys.graft(allparentsData);
          })
       })
      }
    }]);

