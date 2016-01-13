(function(){
'use strict';
    
var filter = angular.module('metaDataApp.metaFilter', []);
filter.factory('filterService', function($http) {
        var filter = {};
        filter.Types= [ {displayName:'All Types', name:'', parent: '' },
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
        filter.Sections= [  { displayName: 'All Sections' , section:'', parent: 'ROOT' },
            { displayName: 'section_run' , section:'section_run', parent: 'ROOT' },
            { displayName: 'section_method' , section:'section_method', parent: 'section_run' },
            { displayName: 'section_system_description' , section:'section_system_description', parent: 'section_run' },
            { displayName: 'section_single_configuration_calculation' , section:'section_single_configuration_calculation', parent: 'section_system_description' },
            { displayName: 'section_scf_iteration' , section:'section_scf_iteration', parent: 'section_run' }  ];
        filter.MetaInfoTypes = [ {displayName: 'All Meta Info Types', name: ''},
//            {displayName: 'type_document', name: 'type_document'},
            {displayName: 'type_dimension', name: 'type_dimension'},
            {displayName: 'type_section', name: 'type_section'},
            {displayName: 'type_document_content', name: 'type_document_content'},
            {displayName: 'type_abstract_document_content', name: 'type_abstract_document_content'}];
        filter.selectedSection= filter.Sections[0] ;
        filter.selectedType=filter.Types[0];
        filter.selectedMetaInfoType=filter.MetaInfoTypes[0];
        var myService = {
            filter:filter,
            setSelected: function(selectedValues){
                filter.selectedSection= selectedValues.selectedSection;
                filter.selectedType=selectedValues.selectedType;
                filter.selectedMetaInfoType=selectedValues.selectedMetaInfoType;
                return filter;
            }
        };
      return myService;
    });

})();