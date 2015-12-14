    var app = angular.module('metaVisualizationApp', ['checklist-model']);
    app.controller('AllDataController', ['$scope', '$http', function($scope, $http) {
      $scope.searchList = [];

      //TODO: Convert angular to Jquery
      $http.get('/nmi/v/last/annotatedinfo.json').success(function(data) {
       var parse = angular.fromJson(data);
       $scope.metaDataList =  parse['metaInfos'];
       $scope.display('section_single_point_evaluation');    
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
      };
      $scope.display = function(data){
         if(typeof data === 'object'){
            $scope.dataToDisplay = data;
            $http.get('/nmi/v/last/n/' + data['name']+'/allparents.json').success(function(allparentsData) {
                        drawGraph(allparentsData['nodes'],allparentsData['edges'])
                      })
         }
         else{
             var i;
             for (i = 0; i < $scope.metaDataList.length ; i++) {
               if($scope.metaDataList[i]['name'] == data){
                 $scope.dataToDisplay = $scope.metaDataList[i];
                         break;
               }
             }
             $http.get('/nmi/v/last/n/' + data+'/allparents.json').success(function(allparentsData) {
                         drawGraph(allparentsData['nodes'],allparentsData['edges'])
                       })
         }


      }


      ///////////////////////////////////////////////////////////////////////////////////
      ///       Cyptoscape Related stuff;
      //////////////////////////////////////////////////////////////////////////////////
   

            var drawGraph = function(exnode,exedge){
      var cy = window.cy = cytoscape({
          container: document.getElementById('cy'),
          boxSelectionEnabled: false,
          autounselectify: true,
          zoomingEnabled: true,
          layout: {
            name: 'dagre',
            rankDir: 'RL'
          },
          style: [
            {
              selector: 'node',
              style: {
                'content': 'data(id)',
                'text-opacity': 0.5,
                'text-valign': 'bottom',
                'text-halign': 'center',
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

