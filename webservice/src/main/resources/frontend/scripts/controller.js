    var app = angular.module('metaVisualizationApp', ['checklist-model','ngSanitize', 'ui.select']);
    app.controller('AllDataController', ['$scope', '$http', function($scope, $http) {
      $scope.searchList = [];
      $scope.filter = {};
      $scope.filter.version = "last";
      //TODO: Convert angular to Jquery
      $http.get('/nmi/info.json').success(function(versions) {
                    $scope.versions =  angular.fromJson(versions)['versions'];
                    console.log($scope.versions)

                  })
      $http.get('/nmi/v/last/annotatedinfo.json').success(function(data) {
       $scope.metaDataList = angular.fromJson(data);
       $scope.display('section_single_configuration_calculation');
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

      $scope.fetchVeriondata = function(version){
        $http.get('/nmi/v/'+version+'/annotatedinfo.json').success(function(data) {
               $scope.metaDataList = angular.fromJson(data);
               $scope.display($scope.dataToDisplay['name']);
                    })
      }
      $scope.display = function(data){
         if(typeof data === 'object'){
            $scope.dataToDisplay = data;
            $http.get('/nmi/v/'+$scope.filter.version+'/n/' + data['name']+'/allparents.json').success(function(allparentsData) {
                        drawGraph(allparentsData['nodes'],allparentsData['edges'])
                      })
         }
         else{
             var i;
             for (i = 0; i < $scope.metaDataList['metaInfos'].length ; i++) {
               if($scope.metaDataList['metaInfos'][i]['name'] == data){
                 $scope.dataToDisplay = $scope.metaDataList['metaInfos'][i];
                         break;
               }
             }
             $http.get('/nmi/v/'+$scope.filter.version+'/n/' + data+'/allparents.json').success(function(allparentsData) {
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

