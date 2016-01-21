(function() {
    'use strict';
    // to set a module
    angular.module('metaDataApp.mainController', ['ngSanitize', 'ui.select','queryFilter','metaDataApp.metaFilter','metaDataApp.dataModule','mathJaxDirective','cytoscapeFactory'])
    .controller('mainController', ['$scope', '$http','$location', 'ancestorGraph', '$routeParams','filterService','dataService',mainController])
    .directive('master',masterDirective);


    function mainController($scope, $http, $location, ancestorGraph, $routeParams,filterService,dataService){
        $scope.metaInfoName = $routeParams.metaInfoName;
        $scope.version = $routeParams.version;
        $scope.dataToDisplay = {
            name:'',
            description:''
        };

        //Header related declarations
        $scope.mappedNames = dataService.mappedNames;
        $scope.filter = filterService.filter;
        //       Version updated; load the new version
        $scope.versionChange = function(version){
            $location.path('/'+version+'/'+$scope.metaInfoName);
        }

        // Probably we should use this function as we have multiple controllers now
        // Update service on filter change; Check if its necessary
        $scope.filterChange = function(selectedValues){
        //    $scope.filter = filterService.setSelected(selectedValues);
        }
        //Header related declarations -- End

        // Get versions
        dataService.asyncVersionList().then(function(versions) {
            $scope.versions =  angular.fromJson(versions)['versions'];
            $scope.versions.sort();
            //Check if requested version exists; otherwise redirect to common
            if($scope.versions.indexOf($scope.version) == -1){
                $scope.version = 'common';
                $location.path('/'+$scope.version+'/'+$scope.metaInfoName);
            }
        });

        //Get version specific data
        dataService.asyncMetaDataList($scope.version).then(function(metaData) {
            $scope.metaDataList = angular.fromJson(metaData);
            $scope.display();
        });

        $scope.set_color = function (derived) {
          if (derived) {
            return { color: "red" }
          }
        }

        //Main function that displays or set the data to be displayed
        $scope.display = function(){
            var i = -1;
            for (i = 0; i < $scope.metaDataList['metaInfos'].length ; i++) {
                if($scope.metaDataList['metaInfos'][i]['name'] == $scope.metaInfoName){
                    $scope.dataToDisplay = $scope.metaDataList['metaInfos'][i];
                    break;
                }
            }
            //If metaData not found then redirect to section_single_configuration_calculation
            if (i == $scope.metaDataList['metaInfos'].length ){
                $location.path('/'+$scope.version+'/section_single_configuration_calculation');
            }
            else{
                dataService.asyncAllParentsCS($scope.version,$scope.metaInfoName ).then(function(allparentsData) {
                    ancestorGraph( allparentsData ).then(function( ancestorCy ){
                        cy = ancestorCy;
                        $scope.cyLoaded = true;
//                        graphOptions(allparentsData);
                        drawChildrenAsList(allparentsData);
                    });
                });
            }
        }

       //Cytoscape related declerations

        $scope.cyLoaded = false; //Not used at the moment
        var cy;
        $scope.DAG = ancestorGraph.zoomButtonSettings;
        var drawChildren = function(allparentsData) {
        var cMinX = 200,
            cMinY = 100,
            currCol = 0,
            currRow = 0,
            maxRow = 7; //max rows in a coloumn

        if( allparentsData.children.length > 0 ) {
            var ele = ancestorGraph.getElementById("Children of "+$scope.metaInfoName)
            var eleX = ele.position().x,
                eleY = ele.position().y;
            var childX = 0,
                childY = 0;
            if(allparentsData.children.length < maxRow){
                maxRow =  allparentsData.children.length;
            }
            for (var i = 0; i < allparentsData.children.length; i++)
            {
                if(currRow >= maxRow) {
                    currRow = 0, currCol += 1;
                }
                childX = eleX + currCol * cMinX + 20; //Default addition
                childY = eleY - ((maxRow -1 )/2) *cMinY + currRow * cMinY;
                allparentsData.children[i].position = {
                    x: childX,
                    y:childY
                }


                if(currCol % 2 == 0)
                    allparentsData.children[i].style["text-valign"] ="top";
                else
                    allparentsData.children[i].style["text-valign"] ="bottom";
                ancestorGraph.add(allparentsData.children[i])
                currRow += 1;
            }
        }
        ancestorGraph.fit();
        ancestorGraph.resize();
        //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
        //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
        ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
    }
        var drawChildrenAsList = function(allparentsData) {
            var cMinX = 0,
                cMinY = 35,
                currCol = 0,
                currRow = 0,
                maxRow = 15, //max rows in a coloumn
                lastColumnX = 0;
            var colMaxWidth = 0;
            if( allparentsData.children.length > 0 ) {
                var ele = ancestorGraph.getElementById("Children of "+$scope.metaInfoName)
                var eleX = ele.position().x,
                    eleY = ele.position().y;
                var childX = 0,
                    childY = 0;
                if(allparentsData.children.length < maxRow){
                    maxRow =  allparentsData.children.length;
                }
                lastColumnX = eleX;
                for (var i = 0; i < allparentsData.children.length; i++)
                {
                    if(currRow >= maxRow) {
                        currRow = 0, currCol += 1;
                        cMinX = colMaxWidth * 7;
//                        console.log("cMinX:", cMinX)
//                        console.log("colMaxWidth:", colMaxWidth)
//                        console.log("currCol:", currCol)
                        lastColumnX = lastColumnX + cMinX ;
                        colMaxWidth = 0;

                    }
                    childX = lastColumnX + cMinX;
                    childY = eleY - ((maxRow -1 )/2) *cMinY + currRow * cMinY;
                    allparentsData.children[i].position = {
                        x: childX,
                        y: childY
                    }
                    allparentsData.children[i].style["text-halign"] ="right";
                    allparentsData.children[i].style["text-valign"] ="center";
                    ancestorGraph.add(allparentsData.children[i])

                    if(allparentsData.children[i].data.id.length > colMaxWidth){
                        colMaxWidth = allparentsData.children[i].data.id.length;
                    }
                    currRow += 1;
                }
            }
            ancestorGraph.fit();
            ancestorGraph.resize();
            //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
            //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
            ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
        }

//      More Cyptoscape Related stuff;
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
           ancestorGraph.zoomPanToggle($scope.DAG.zoom);
        };
    };

    function masterDirective($window) { //declaration; identifier master
        function link(scope, element, attrs) { //scope we are in, element we are bound to, attrs of that element
          scope.$watch(function(){ //watch any changes to our element
//          console.log($window)
//          console.log(element)
          console.log("Window Height: "+ angular.element($window).height()+ "  "+$window.innerHeight + "  "+$window +" Element height: " +element[0].offsetHeight  )
            scope.style = { //scope variable style, shared with our controller
//              height: ( angular.element($window).height() - element[0].offsetHeight )+'px' //set the height in style to our elements height
              height: ( $window.innerHeight - element[0].offsetHeight )+'px' //set the height in style to our elements height
              };
          });
        }
          return {
            restrict: 'AE', //describes how we can assign an element to our directive in this case like <div master></div
            link: link // the function to link to our element
          };
    };
})();
