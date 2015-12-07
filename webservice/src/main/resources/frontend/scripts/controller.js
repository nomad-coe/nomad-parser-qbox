    angular.module('metaVisualizationApp', ["checklist-model"])
    .controller('AllDataController', ['$scope', '$http', function($scope, $http) {
      $scope.searchList = [];

      //TODO:Replace arborjs with cytoscapejs
      //TODO: Convert angular to Jquery
      $http.get('/nmi/v/last/info.json').success(function(data) {
       var parse = angular.fromJson(data);
       $scope.metaDataList =  parse['metaInfos'];
       $scope.display($scope.metaDataList[0]['name']);    
     })
           
      $scope.addToList = function(str){
        if ($scope.searchList.indexOf(str) >= 0) {
          var index = $scope.searchList.indexOf(str);
          if (index > -1) {
           $scope.searchList.splice(index, 1);
          }
        }
        else {
          $scope.searchList.push(str); 
        }
      }
      $scope.display = function(data){
        $http.get('/nmi/v/last/n/' + data+'/annotated.json').success(function(resData) {
         $scope.dataToDisplay = angular.fromJson(resData);

          $http.get('/nmi/v/last/n/' + data+'/allparents.json').success(function(allparentsData) {
            drawGraph(allparentsData['nodes'],allparentsData['edges'])
          })
       })
      }


      ///////////////////////////////////////////////////////////////////////////////////
      ///       Cyptoscape Related stuff;
      //////////////////////////////////////////////////////////////////////////////////
   

            var drawGraph = function(exnode,exedge){
      var cy = window.cy = cytoscape({
          container: document.getElementById('cy'),
          boxSelectionEnabled: false,
          autounselectify: true,
          layout: {
            name: 'dagre'
          },
          style: [
            {
              selector: 'node',
              style: {
                'content': 'data(id)',
                'text-opacity': 0.5,
                'text-valign': 'center',
                'text-halign': 'right',
                'background-color': '#11479e'
              }
            },
            {
              selector: 'edge',
              style: {
                'width': 4,
                'target-arrow-shape': 'triangle',
                'line-color': '#9dbaea',
                'target-arrow-color': '#9dbaea'
              }
            }
          ],
          elements: {
            nodes: exnode ,
            edges: exedge
          },
        });
        cy.on('click', 'node', function(evt){
          var toDisplay = this.id()
          
          $scope.display(toDisplay.split(" -")[0])
          console.log( 'clicked ' + toDisplay.split(" -")[0] );
        });
    }
    }]);

