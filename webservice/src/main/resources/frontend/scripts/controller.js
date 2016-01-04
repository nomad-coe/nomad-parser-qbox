    var app = angular.module('metaVisualizationApp', ['checklist-model','ngSanitize', 'ui.select']);
    app.controller('AllDataController', ['$scope', '$http', function($scope, $http) {
      $scope.searchList = [];
      $scope.filter = {};
      $scope.filter.Parents= [  { displayName: 'NO FILTER' , section:'', parent: 'ROOT' },
                                { displayName: 'section_run' , section:'section_run', parent: 'ROOT' },
                                { displayName: 'section_system_description' , section:'section_system_description', parent: 'section_run' },
                                { displayName: 'section_single_configuration_calculation' , section:'section_single_configuration_calculation', parent: 'section_system_description' },
                                { displayName: 'section_atom_kind' , section:'section_atom_kind', parent: 'section_system_description' },
                                { displayName: 'section_method' , section:'section_method', parent: 'section_run' },
                                { displayName: 'section_single_configuration_calculation' , section:'section_single_configuration_calculation', parent: 'section_method' },
                                { displayName: 'section_atom_ref' , section:'section_atom_ref', parent: 'section_method' },
                                { displayName: 'section_atom_kind_ref' , section:'section_atom_kind_ref', parent: 'section_method' },
                                { displayName: 'section_k_band' , section:'section_k_band', parent: 'section_run' }  ];
      $scope.filter.selectedParent= { displayName: 'NO FILTER' , section:'', parent: 'ROOT' } ;
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
         // console.log( 'clicked ' + toDisplay.split(" -")[0] );
        });
    }
    }]);
app.directive('master',function ($window) { //declaration; identifier master
    function link(scope, element, attrs) { //scope we are in, element we are bound to, attrs of that element
      scope.$watch(function(){ //watch any changes to our element
        scope.style = { //scope variable style, shared with our controller
//        console.log("Inside Style: " + angular.element($window).height());
            height: ( angular.element($window).height() - element[0].offsetHeight )+'px', //set the height in style to our elements height
          };
      });
    }
      return {
        restrict: 'AE', //describes how we can assign an element to our directive in this case like <div master></div
        link: link // the function to link to our element
      };
});
app.directive('resize',function ($window) { //declaration; identifier master
    function link(scope, element, attrs) { //scope we are in, element we are bound to, attrs of that element
      scope.$watch(function(){ //watch any changes to our element
        scope.style2 = { //scope variable style, shared with our controller
            height: angular.element($window).height() +'px', //set the height in style to our elements height
          };
      });
    }
      return {
        restrict: 'AE', //describes how we can assign an element to our directive in this case like <div master></div
        link: link // the function to link to our element
      };
});
