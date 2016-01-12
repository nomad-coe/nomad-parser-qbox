(function() {
    'use strict';
    // to set a module
    angular.module('metaDataApp.mainController', ['ngSanitize', 'ui.select','angular.filter','metaDataApp.metaFilter','metaDataApp.dataModule'])
    .controller('mainController', ['$scope', '$http','$location', 'ancestorGraph', '$routeParams','filterService','dataService',mainController])
    .factory('ancestorGraph', ['$q','$location','$rootScope', ancestorGraph])
    .directive('master',masterDirective);
//TODO: Filtering not working
    function mainController($scope, $http, $location, ancestorGraph, $routeParams,filterService,dataService){
        $scope.metaInfoName = $routeParams.metaInfoName;
        $scope.version = $routeParams.version;
        $scope.filter = filterService.filter;
        console.log($routeParams.metaInfoName, $routeParams.version);
        dataService.asyncVersionList().then(function(versions) {
            console.log("Recieved versions now. Async called : asyncVersionList")
            $scope.versions =  angular.fromJson(versions)['versions'];
            $scope.versions.sort();
        });

        //It knows the versions; as the current version is stored in the service; So the version string is not passed when fetching the data
        dataService.asyncMetaDataList($scope.version).then(function(metaData) {
            console.log("Recieved metaDataList.")
            $scope.metaDataList = angular.fromJson(metaData);
            $scope.display();
        });

        $scope.display = function(){
            for (var i = 0; i < $scope.metaDataList['metaInfos'].length ; i++) {
                if($scope.metaDataList['metaInfos'][i]['name'] == $scope.metaInfoName){
                    $scope.dataToDisplay = $scope.metaDataList['metaInfos'][i];
                    break;
                }
            }
           // TODO: Move to service
            dataService.asyncAllParents($scope.version,$scope.metaInfoName ).then(function(allparentsData) {
                ancestorGraph( allparentsData ).then(function( ancestorCy ){
                cy = ancestorCy;
                ancestorGraph.zoomToggle( $scope.DAG.zoom); //Override the default settings
                $scope.cyLoaded = true;
                ancestorGraph.resize();
                });
            });
        }

        //The version string is now updated
         $scope.versionChange = function(version){
            console.log("versionChange:",version);
            $location.path('/'+version+'/'+$scope.metaInfoName);
//            dataService.setVersion(version);
//            $scope.version = dataService.getVersion(); //Verifies that the version was set correctly
        }
        var cy;
        $scope.DAG ={
            zoom:false,
            zoomText:'Enable zoom'
        }

        ///////////////////////////////////////////////////////////////////////////////////
        ///       Cyptoscape Related stuff;                                             ///
        ///////////////////////////////////////////////////////////////////////////////////
        ancestorGraph.onClick(function(id){
          $scope.display(id)
          $scope.$apply();
        });
        $scope.reset = function(){
         ancestorGraph.reset();
        }
        $scope.zoomToggle = function(){
           if ( $scope.DAG.zoom){
                    $scope.DAG.zoom=false;
                    $scope.DAG.zoomText='Enable zoom';
           }
           else {
                  $scope.DAG.zoom=true;
                  $scope.DAG.zoomText='Disable zoom';
           }
          ancestorGraph.zoomToggle( $scope.DAG.zoom);
        };

    };
    //The ancestorGraph service, defined using a factory
    //Uuuuuu dangerous; TODO: replace rootScope
    function ancestorGraph($q,$location,$rootScope){
      var cy;
      var ancestorGraph = function(elem){
        var deferred = $q.defer();
        $(function(){ // on dom ready
          cy = cytoscape({
            container: $('#cy')[0],
            boxSelectionEnabled: true,
            autounselectify: true,
            zoomingEnabled: false,
            style: cytoscape.stylesheet()
              .selector('node')
                .css({
                  'content': 'data(id)',
                  'text-opacity': 0.5,
                  'text-valign': 'bottom',
                  'text-halign': 'center',
                  'background-color': '#11479e'
                 })
              .selector('edge')
                .css({
                  'width': 4,
                  'target-arrow-shape': 'triangle',
                  'line-color': '#9dbaea',
                  'target-arrow-color': '#9dbaea'
                }),

            layout: {
              name: 'dagre',
              rankDir: 'RL'
            },

            elements: elem,

            ready: function(){
              deferred.resolve( this );

              this.on('click', 'node', function(evt){
                console.log($location.path());
                var path = angular.copy($location.path());
                var splitPath = path.split("/");
                $location.path(splitPath.slice(0, splitPath.length - 1).join("/") + "/" + this.id());
                $rootScope.$apply();
//                        fire('onClick', [ this.id().split(" -")[0] ]);
              });
            }
          });

        }); // on dom ready

        return deferred.promise;
      };

      ancestorGraph.listeners = {};

      function fire(e, args){
        var listeners = ancestorGraph.listeners[e];

        for( var i = 0; listeners && i < listeners.length; i++ ){
          var fn = listeners[i];
          fn.apply( fn, args );
        }
      }

      function listen(e, fn){
        var listeners = ancestorGraph.listeners[e] = ancestorGraph.listeners[e] || [];
        listeners.push(fn);
      }

      ancestorGraph.resize = function(){
           cy.resize();
        }
      ancestorGraph.reset = function(){
         var czoom = cy.zoomingEnabled();
         cy.zoomingEnabled(true);
         cy.reset();
         cy.zoomingEnabled(czoom);
      }
      ancestorGraph.zoomToggle = function(zoom){
    //      console.log(cy);
    //      console.log(cy.zoomingEnabled());
          cy.zoomingEnabled(zoom);
        };

      ancestorGraph.onClick = function(fn){
        listen('onClick', fn);
      };
      return ancestorGraph;
    };

    function masterDirective($window) { //declaration; identifier master
        function link(scope, element, attrs) { //scope we are in, element we are bound to, attrs of that element
          scope.$watch(function(){ //watch any changes to our element
            scope.style = { //scope variable style, shared with our controller
                height: ( angular.element($window).height() - element[0].offsetHeight )+'px' //set the height in style to our elements height
              };
          });
        }
          return {
            restrict: 'AE', //describes how we can assign an element to our directive in this case like <div master></div
            link: link // the function to link to our element
          };
    };
})();