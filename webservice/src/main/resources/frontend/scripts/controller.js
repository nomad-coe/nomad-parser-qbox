    var app = angular.module('metaVisualizationApp', ['ngRoute','ngSanitize', 'ui.select','angular.filter','hc.marked']);
    app.config(['$routeProvider',
      function($routeProvider) {
        $routeProvider.
          when('/:metaInfoId', {
            templateUrl: 'views/metaInfo.html',
            controller: 'AllDataController'
          }).
          otherwise({
            redirectTo: '/section_single_configuration_calculation'
          });
      }]);
    app.controller('AllDataController', ['$scope', '$http', 'ancestorGraph', '$routeParams',function($scope, $http, ancestorGraph,$routeParams) {
      $scope.metaInfoId = $routeParams.metaInfoId;
      console.log($routeParams.metaInfoId);
      $scope.searchList = [];
      $scope.filter = {};
      $scope.filter.Types= [      {displayName:'All Types', name:'', parent: '' },
                                  {displayName: 'energy',name:'energy', parent: 'Energy' },
                                  {displayName: 'energy_component_scf_iteration',name:'energy_component_scf_iteration', parent: 'Energy' },
                                  {displayName: 'energy_component_per_atom_scf_iteration',name:'energy_component_per_atom_scf_iteration',parent: 'Energy' },
                                  {displayName: 'energy_component',name:'energy_component',parent: 'Energy' },
                                  {displayName: 'energy_component_per_atom',name:'energy_component_per_atom',parent: 'Energy' },
                                  {displayName: 'energy_total_potential',name:'energy_total_potential',parent: 'Energy' },
//                                  # parent: 'Settings' },
                                  {displayName: 'settings_GW',name:'settings_GW', parent: 'Settings' },
                                  {displayName: 'settings_MCSCF',name:'settings_MCSCF', parent: 'Settings' },
                                  {displayName: 'settings_XC',name:'settings_XC', parent: 'Settings' },
                                  {displayName: 'settings_XC_functional',name:'settings_XC_functional', parent: 'Settings' },
                                  {displayName: 'settings_XC_functional_scf',name:'settings_XC_functional_scf', parent: 'Settings' },
                                  {displayName: 'settings_XC_scf',name:'settings_XC_scf', parent: 'Settings' },
                                  {displayName: 'settings_atom_kind',name:'settings_atom_kind', parent: 'Settings' },
                                  {displayName: 'settings_coupled_cluster',name:'settings_coupled_cluster', parent: 'Settings' },
                                  {displayName: 'settings_method',name:'settings_method', parent: 'Settings' },
                                  {displayName: 'settings_moller_plesset_perturbation_theory',name:'settings_moller_plesset_perturbation_theory', parent: 'Settings' },
                                  {displayName: 'settings_multi_reference',name:'settings_multi_reference', parent: 'Settings' },
                                  {displayName: 'settings_post_hartree_fock',name:'settings_post_hartree_fock', parent: 'Settings' },
                                  {displayName: 'settings_relativity_treatment',name:'settings_relativity_treatment', parent: 'Settings' },
                                  {displayName: 'settings_self_interaction_correction',name:'settings_self_interaction_correction', parent: 'Settings' },
                                  {displayName: 'settings_simulation',name:'settings_simulation', parent: 'Settings' },
                                  {displayName: 'settings_single_configuration_method',name:'settings_single_configuration_method', parent: 'Settings' },
                                  {displayName: 'settings_van_der_Waals_treatment',name:'settings_van_der_Waals_treatment', parent: 'Settings' },
//                                  # parent: 'Accessory Info' },
                                  {displayName: 'accessory_info',name:'accessory_info', parent: 'Accessory Info' },
                                  {displayName: 'parallelization_info',name:'parallelization_info', parent: 'Accessory Info' },
                                  {displayName: 'program_info',name:'program_info', parent: 'Accessory Info' },
                                  {displayName: 'time_info_run',name:'time_info_run', parent: 'Accessory Info' },
                                  {displayName: 'time_info_evaluation',name:'time_info_evaluation', parent: 'Accessory Info' },
                                  {displayName: 'time_info_scf_iteration',name:'time_info_scf_iteration', parent: 'Accessory Info' }  ];
      $scope.filter.Sections= [  { displayName: 'All Sections' , section:'', parent: 'ROOT' },
                                { displayName: 'section_run' , section:'section_run', parent: 'ROOT' },
                                { displayName: 'section_method' , section:'section_method', parent: 'section_run' },
                                { displayName: 'section_system_description' , section:'section_system_description', parent: 'section_run' },
                                { displayName: 'section_single_configuration_calculation' , section:'section_single_configuration_calculation', parent: 'section_system_description' },
                                { displayName: 'section_scf_iteration' , section:'section_scf_iteration', parent: 'section_run' }  ];
      $scope.filter.MetaInfoTypes = [ {displayName: 'All Meta Info Types', name: ''},
                                  {displayName: 'type_document', name: 'type_document'},
                                  {displayName: 'type_dimension', name: 'type_dimension'},
                                  {displayName: 'type_section', name: 'type_section'},
                                  {displayName: 'type_document_content', name: 'type_document_content'},
                                  {displayName: 'type_abstract_document_content', name: 'type_abstract_document_content'}];
      $scope.filter.selectedSection= { displayName: 'All Sections' , section:'', parent: 'ROOT' } ;
      $scope.filter.selectedType={displayName:'All Types', name:'', parent: 'Energy' };
      $scope.filter.selectedMetaInfoType={displayName:'All Meta Info Types', name:''};

      $scope.dataToDisplay = {};
//      $scope.dataToDisplay.name = 'section_single_configuration_calculation';
      $scope.dataToDisplay.name = $routeParams.metaInfoId;
      $scope.filter.version = "common";
      $scope.DAG ={
          zoom:false,
          zoomText:'Enable zoom'
      }
      //TODO: Convert angular to Jquery
      $http.get('/nmi/info.json').success(function(versions) {
                    $scope.versions =  angular.fromJson(versions)['versions'];
                    $scope.versions.sort();
                    $scope.fetchVeriondata($scope.filter.version)
                  })
      $scope.fetchVeriondata = function(version){
        $http.get('/nmi/v/'+version+'/annotatedinfo.json').success(function(data) {
              $scope.metaDataList = angular.fromJson(data);
              $scope.display($scope.dataToDisplay['name']);
        })
      }
      var cy;
      $scope.display = function(data){
         if(typeof data === 'object'){
            $scope.dataToDisplay = data;
            $http.get('/nmi/v/'+$scope.filter.version+'/n/' + data['name']+'/allparents.json').success(function(allparentsData) {
                            ancestorGraph( allparentsData ).then(function( ancestorCy ){
                                cy = ancestorCy;
                                ancestorGraph.zoomToggle( $scope.DAG.zoom); //Override the default settings
                                $scope.cyLoaded = true;
                                ancestorGraph.resize();
                              });

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
                          ancestorGraph( allparentsData ).then(function( ancestorCy ){
                              cy = ancestorCy;
                              ancestorGraph.zoomToggle( $scope.DAG.zoom); //Override the default settings
                              $scope.cyLoaded = true;
                              ancestorGraph.resize();
                            });
                       })
         }
      }

//      $scope.addToList = function(str){
//        if ($scope.searchList.indexOf(str) >= 0) {
//          var index = $scope.searchList.indexOf(str);
//          if (index > -1) {
//           $scope.searchList.splice(index, 1);
//          }
//        }
//        else {
//          $scope.searchList.push(str);
//        }
//      };
      ///////////////////////////////////////////////////////////////////////////////////
      ///       Cyptoscape Related stuff;
      //////////////////////////////////////////////////////////////////////////////////

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

    }]);

app.factory('ancestorGraph', [ '$q', function( $q ){
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
                    fire('onClick', [ this.id().split(" -")[0] ]);
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

  ancestorGraph.setPersonWeight = function(id, weight){
    cy.$('#' + id).data('weight', weight);
  };

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
} ]);

app.service('versionService',function(){

    this.allVersion = function(){

    }
    this.allVersion = function(){

    }

})
app.directive('master',function ($window) { //declaration; identifier master
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
});
app.directive('resize',function ($window) { //declaration; identifier master
    function link(scope, element, attrs) { //scope we are in, element we are bound to, attrs of that element
      scope.$watch(function(){ //watch any changes to our element
        scope.style2 = { //scope variable style, shared with our controller
            height: angular.element($window).height() +'px' //set the height in style to our elements height
          };
      });
    }
      return {
        restrict: 'AE', //describes how we can assign an element to our directive in this case like <div master></div
        link: link // the function to link to our element
      };
});
