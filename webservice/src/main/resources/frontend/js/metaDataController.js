(function() {
    'use strict';
    // to set a module
    angular.module('metaDataApp.mainController', ['ngSanitize', 'ui.select','queryFilter','metaDataApp.metaFilter','metaDataApp.dataModule','mathJaxDirective','cytoscapeFactory'])
    .controller('mainController', ['$scope', '$http','$location', 'ancestorGraph', '$routeParams','filterService','dataService',mainController])
    .directive('master',masterDirective);


    function mainController($scope, $http, $location, ancestorGraph, $routeParams,filterService,dataService){
        $scope.metaInfoName = $routeParams.metaInfoName;
        $scope.version = $routeParams.version;
        $scope.cyCSS= {
        height:"550px",
        width:"900px"
        }
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
            $scope.versions = sortVersion( angular.fromJson(versions)['versions']);
//            $scope.versions.sort();
            //Check if requested version exists; otherwise redirect to common
            if($scope.versions.indexOf($scope.version) == -1){
                $scope.version = 'common';
                $location.path('/'+$scope.version+'/'+$scope.metaInfoName);
            }
        });
        var sortVersion = function(ver){
            var nVer1 = {};
            var nVer2 = [];
            var res = [];
            for(var i = 0; i < ver.length; i++){
                nVer2.push($scope.mappedNames.version(ver[i]));
                nVer1[$scope.mappedNames.version(ver[i])] = ver[i];
            }
            nVer2.sort();
            for(i = 0; i < nVer2.length; i++){
                res.push(nVer1[nVer2[i]])
            }
            if(res.indexOf("common") > -1){
                res.splice(res.indexOf("common"),1);
                res.splice(1, 0, "common");
            }
            return res;
        };

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
                dataService.asyncMetaInfoAncestorChildrenGraph($scope.version,$scope.metaInfoName ).then(function(allParentsData) {
                //Change the height of the element depending upon the number of children
                if( allParentsData.children.length < 10) {
                    $scope.cyCSS.height = "450px";
                }
//                else if( allParentsData.children.length > 20 ) {
//                    $scope.cyCSS.height = "700px";
//                }
                ancestorGraph( allParentsData ).then(function( ancestorCy ){
//                    drawChildrenAsList(allParentsData);
                    drawChildrenAsListBelowTheRoot(allParentsData);
                    ancestorGraph.resize();
                    ancestorGraph.reset();
                    ancestorGraph.reset(); //Double call magically solves some formatting problems
                    ancestorGraph.fit();
                    //Nothing works if panning and zoom is disabled; since resize and fit needs to pan and zoom :P
                    //Note: ancestorGraph functions needs pan and zoom to be enabled to operate keep that in mind
                    ancestorGraph.zoomPanToggle( $scope.DAG.zoom); //Override the default settings
                });
                });
            }
        }

       //Cytoscape related declerations

        $scope.DAG = ancestorGraph.zoomButtonSettings;

        var drawChildrenAsList = function(allParentsData) {
            var cMinX = 0,
                cMinY = 35,
                currCol = 0,
                currRow = 0,
                maxRow = 20, //max rows in a coloumn
                columnX = 0;
            var colMaxWidth = 0;
            if( allParentsData.children.length > 0 ) {
                if(allParentsData.children.length < maxRow){
                    maxRow =  allParentsData.children.length;
                }
                else {
                //Reset the maxRow for max fit in each row;
                var nc = Math.ceil(allParentsData.children.length / maxRow);
                maxRow = Math.ceil(allParentsData.children.length / nc)
                }

                var eles = ancestorGraph.elements("*");
                var options = {includeNodes: true, includeEdges: true, includeLabels:true };
                var bBox = eles.boundingBox(options);
                console.log(eles)
                console.log(bBox)
                var eleX = bBox.x2 + 40, // move the node to make space for other children
                    eleY = (bBox.y1 + bBox.y2)/2 // - (maxRow*cMinY)/2;
                var childX = 0,
                    childY = 0;
                columnX = eleX + 20;

                var bracketContainer =  [
                                         { group: "nodes", classes: 'bracket', data: { id: "P1" }, position: {x:eleX, y:eleY }, style:{width:5,height:5} },
                                         { group: "nodes", classes: 'bracket', data: { id: "P2" }, position: {x:eleX, y:eleY + (maxRow/2 ) * cMinY + 10 }, style:{width:5,height:5} },
                                         { group: "nodes", classes: 'bracket', data: { id: "P3" }, position: {x:eleX, y:eleY - (maxRow/2 ) * cMinY - 10}, style:{width:5,height:5} },
                                         { group: "nodes", classes: 'bracket', data: { id: "P4" }, position: {x:eleX + 20, y:eleY + (maxRow/2 ) * cMinY + 10}, style:{width:5,height:5} },
                                         { group: "nodes", classes: 'bracket', data: { id: "P5" }, position: {x:eleX + 20, y:eleY - (maxRow/2 ) * cMinY - 10}, style:{width:5,height:5} },
                                         { group: "nodes", data: { id: "Direct Children" }, position: {x:eleX + 20, y:eleY + (maxRow/2 ) * cMinY + 30}, style:{width:0,height:0} },
//                                         { group: "edges", data: { id: "P1_" + $scope.metaInfoName  , source: "P1", target: $scope.metaInfoName }, style: { width: 1,  label: "Direct Children" } },
                                         { group: "edges", data: { id: "P2_P1"  , source: "P2", target: "P1" }, style: {"curve-style": "haystack", width: 5} },
                                         { group: "edges", data: { id: "P3_P1"  , source: "P3", target: "P1" }, style: {"curve-style": "haystack", width: 5} },
                                         { group: "edges", data: { id: "P4_P2"  , source: "P4", target: "P2" }, style: {"curve-style": "haystack", width: 5} },
                                         { group: "edges", data: { id: "P5_P3"  , source: "P5", target: "P3" }, style: {"curve-style": "haystack", width: 5} }
                                       ];

                ancestorGraph.add(bracketContainer);
                for (var i = 0; i < allParentsData.children.length; i++)
                {
                    if(currRow >= maxRow) {
                        currRow = 0, currCol += 1;
                        cMinX = colMaxWidth * 10;
                        columnX +=  cMinX ;
                        colMaxWidth = 0;
                    }
                    childX = columnX;
                    childY = eleY - ((maxRow -1 )/2) *cMinY + currRow * cMinY;

                    console.log(childX + " " +childY );


                    allParentsData.children[i].position = {
                        x: childX,
                        y: childY
                    }
                    allParentsData.children[i].style["text-halign"] ="right";
                    allParentsData.children[i].style["text-valign"] ="center";
                    ancestorGraph.add(allParentsData.children[i])

                    if(allParentsData.children[i].data.id.length > colMaxWidth){
                        colMaxWidth = allParentsData.children[i].data.id.length;
                    }
                    currRow += 1;
                }
            }
        }
        var drawChildrenAsListBelowTheRoot = function(allParentsData) {
            var cMinX = 0,
                cMinY = 35,
                currCol = 0,
                currRow = 0,
                maxRow = 10, //max rows in a coloumn
                columnX = 0;
            var colMaxWidth = 0;

            if( allParentsData.children.length > 0 ) {
                if(allParentsData.children.length < maxRow){
                    maxRow =  allParentsData.children.length;
                }
                else {
                //Reset the maxRow for max fit in each row;
                var nc = Math.ceil(allParentsData.children.length / maxRow);
                maxRow = Math.ceil(allParentsData.children.length / nc)
                }

                var eles = ancestorGraph.elements("*");
                var options = {includeNodes: true, includeEdges: true, includeLabels:true };
                var bBox = eles.boundingBox(options);
                console.log(eles)
                console.log(bBox)
                var eleX = (bBox.x1 + bBox.x2)/2 - 100*allParentsData.children.length/maxRow,  // move the node to make space for other children
                    eleY = bBox.y2 + (maxRow * cMinY) /2 + 100;
                var childX = 0,
                    childY = 0;

                columnX = eleX + 80;
                var bracketContainer =  { group: "nodes", classes: "DC", data: { id: $scope.metaInfoName + " direct children:" }, position: {x:eleX + 160, y:bBox.y2 + 50}, style:{width:0,height:0} };
                ancestorGraph.add(bracketContainer);
                for (var i = 0; i < allParentsData.children.length; i++)
                {
                    if(currRow >= maxRow) {
                        currRow = 0, currCol += 1;
                        cMinX = colMaxWidth * 10;
                        columnX +=  cMinX ;
                        colMaxWidth = 0;
                    }
                    childX = columnX;
                    childY = eleY - ((maxRow -1 )/2) *cMinY + currRow * cMinY;
                    allParentsData.children[i].position = {
                        x: childX,
                        y: childY
                    }
                    allParentsData.children[i].style["text-halign"] ="right";
                    allParentsData.children[i].style["text-valign"] ="center";
                    ancestorGraph.add(allParentsData.children[i])

                    if(allParentsData.children[i].data.id.length > colMaxWidth){
                        colMaxWidth = allParentsData.children[i].data.id.length;
                    }
                    currRow += 1;
                }
            }
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
