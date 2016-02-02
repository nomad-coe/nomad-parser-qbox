describe('searchAndQuery filter', function() {
  var $filter;
  var searchAndQueryFilter, results;
  var metaItems, searchFilter, sectionFilter, allParentsFilter,metaInfoTypeFilter, derivedFilter;

  beforeEach(module('queryFilter'));
  beforeEach(inject(function(_$filter_){
    $filter = _$filter_;
  }));
  beforeEach(function(){
    searchAndQueryFilter = $filter('searchAndQueryFilter');
  });
  beforeEach(function(){
    searchFilter = "", sectionFilter ="", allParentsFilter ="",metaInfoTypeFilter ="", derivedFilter ="";
    results = [];
    metaItems = [
            {
              "type":"nomad_meta_versions_1_0",
              "versions":"last",
              "name":"number_of_k_point_segments",
              "description":"<p>number of k point segments</p>",
              "gid":"pm_7R2Q7p6hqCe9nPErnsmNalSbH",
              "dtypeStr":"i (integer value)",
              "kindStr":"type_dimension",
              "derived":true,
              "superNames":[
                "section_k_band"
              ],
              "children":[

              ],
              "allparents":[
                "section_run",
                "section_single_configuration_calculation",
                "section_k_band",
                "number_of_k_point_segments"
              ],
              "rootSectionAncestors":[
                "section_k_band"
              ],
              "shape":[

              ]
            },
            {
              "type":"nomad_meta_versions_1_0",
              "versions":"last",
              "name":"basis_set_atom_number",
              "description":"<p>atomic number (number of protons) of the atom this basis set is thought for (0 means unspecified, or a pseudo atom)</p>",
              "gid":"pN96LtLXwMBXRyTwUzB-J-4xeOTr",
              "dtypeStr":"i (integer value)",
              "kindStr":"type_document_content",
              "superNames":[
                "section_basis_set_atom_centered"
              ],
              "children":[

              ],
              "allparents":[
                "section_run",
                "basis_set_description",
                "section_basis_set_atom_centered",
                "basis_set_atom_number"
              ],
              "rootSectionAncestors":[
                "section_k_band"
              ],
              "shape":[

              ]
            },
            {
              "type":"nomad_meta_versions_1_0",
              "versions":"last",
              "name":"energy_hartree_fock_X",
              "description":"<p>Converged exact exchange energy (not scaled). Defined consistently with <a href=\"#/last/XC_method\"> XC_method </a> </p>",
              "gid":"pKwJ9RPMfn_r1zxjZPUQFIl8CtHx",
              "units":"J",
              "dtypeStr":"f (floating point value)",
              "repeats":false,
              "kindStr":"type_document_content",
              "superNames":[
                "energy_type_X"
              ],
              "children":[

              ],
              "allparents":[
                "section_run",
                "energy_value",
                "section_single_configuration_calculation",
                "energy_component",
                "energy_type_X",
                "energy_hartree_fock_X"
              ],
              "rootSectionAncestors":[
                "section_single_configuration_calculation"
              ],
              "shape":[

              ]
            }
          ];
  });

  it('all filters: returns unmodified when filters are null', function() {
    expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(metaItems);
  });
  
  it('search filter: returns metaItems that shows all that contain the search string', function() {
    results.push(metaItems[0]);
    results.push(metaItems[1]);
    searchFilter = 'number';
    expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('search filter: returns only that contain the search string, case of random string', function() {
    searchFilter = 'alibabababa';
    expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual([]);
  });

  it('section filter: returns only that have the given section as rootAncestorSection', function() {
      results.push(metaItems[2]);
      sectionFilter = 'section_single_configuration_calculation';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('section and search filter: returns only that have the given section as rootAncestorSection and satisfy the search', function() {
      results.push(metaItems[0]);
      sectionFilter = 'section_k_band';
      searchFilter = 'segments';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('allParentsFilter filter: returns only that have the given meta item in supername, case of section_run', function() {
      allParentsFilter = 'section_run';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(metaItems);
  });

  it('allParentsFilter filter: returns only that have the given meta item in supername', function() {
      results.push(metaItems[2]);
      allParentsFilter = 'energy_type_X';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('metaInfoTypeFilter filter: returns only that have the given type in kindStr', function() {
      results.push(metaItems[1]);
      results.push(metaItems[2]);
      metaInfoTypeFilter = 'type_document_content';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('metaInfoTypeFilter and search filter: returns only that have the given type in kindStr', function() {
      results.push(metaItems[1]);
      searchFilter = 'number';
      metaInfoTypeFilter = 'type_document_content';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('derivedFilter filter: returns only that are derived', function() {
      results.push(metaItems[1]);
      results.push(metaItems[2]);
      derivedFilter = true;
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual(results);
  });

  it('derivedFilter and search filter: returns only that are derived', function() {
      derivedFilter = true;
      searchFilter = 'segments';
      expect(searchAndQueryFilter( metaItems, searchFilter, sectionFilter, allParentsFilter, metaInfoTypeFilter, derivedFilter)).toEqual([]);
  });
});