(function() {
    'use strict';
    // to set a module
    angular.module('graphApp.graphController', ['ngSanitize', 'ui.select','angular.filter','metaDataApp.metaFilter','metaDataApp.dataModule','mathJaxDirective'])
    .controller('graphController', ['$scope', '$http','$location', 'ancestorGraph', '$routeParams','filterService','dataService',graphController]);
//    .factory('ancestorGraph', ['$q','$location','$rootScope', ancestorGraphDeclaration])

    function graphController($scope, $http, $location, ancestorGraph, $routeParams,filterService,dataService){
        console.log("New controller!");
        $scope.filter = filterService.filter;
        $scope.version = 'common';

        //Header related declarations
        $scope.mappedNames = dataService.mappedNames;
        $scope.filter = filterService.filter;
        //       Version updated; load the new version
        $scope.versionChange = function(version){
            getVersion(version);
        }

        // Probably we should use this function as we have multiple controllers now
        // Update service on filter change; Check if its necessary
        $scope.filterChange = function(selectedValues){
        //    $scope.filter = filterService.setSelected(selectedValues);
        }
        //Header related declarations -- End

        //Note; Get data from service; Code similar to the main controller with minute differences
        //Difference in getting version etc.
        // Get versions
        dataService.asyncVersionList().then(function(versions) {
            $scope.versions =  angular.fromJson(versions)['versions'];
            $scope.versions.sort();
            //Check if requested version exists; otherwise redirect to common
            if($scope.versions.indexOf($scope.version) == -1){
                $scope.version = 'common';
            }
            getVersion($scope.version);
        });

        //Get version specific data
        var getVersion = function(version){
            dataService.asyncMetaDataList(version).then(function(metaData) {
                $scope.metaDataList = angular.fromJson(metaData);
            });
        }

        $scope.DAG = ancestorGraph.zoomButtonSettings;

        $scope.drawGraph = function(metaInfos){
            console.log($scope.combinedGraph.selectedMetaInfos);
            $scope.combinedGraph = {
                selectedMetaInfos:[]
            }
            var allData = {
                nodes: [],
                edges: []
            }
            for(var i= 0; i< metaInfos.length;i++)
            {
                dataService.asyncAllParentsCS($scope.version,metaInfos[i]).then(function(d) {
//                        return allparentsData;
                allData.nodes = allData.nodes.concat(d.nodes);
                allData.edges = allData.edges.concat(d.edges);
                });
//                allData.edges = allData.edges.concat(d.edges);
            }
            console.log(allData);

            ancestorGraph( allData ).then(function( ancestorCy ){
                cy = ancestorCy;
                $scope.cyLoaded = true;
                ancestorGraph.fit();
                ancestorGraph.resize();
                //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
                //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
                ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
            });

        }
    }
})();