var metaInfos = [
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"number_of_k_point_segments",
          "description":"<p>number of k point segments</p>",
          "gid":"pm_7R2Q7p6hqCe9nPErnsmNalSbH",
          "dtypeStr":"i (integer value)",
          "kindStr":"type_dimension",
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
            "section_basis_set_atom_centered"
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
        },
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"message_info_evaluation",
          "description":"<p>An information message of the computational program, associated with a a single configuration calculation</p>",
          "gid":"pgjnvKuJP34Vcd1fDxNlYGHLwNcE",
          "dtypeStr":"C (a unicode string)",
          "kindStr":"type_document_content",
          "superNames":[
            "message_info",
            "section_single_configuration_calculation"
          ],
          "children":[

          ],
          "allparents":[
            "section_run",
            "message_debug",
            "section_single_configuration_calculation",
            "message_info",
            "message_info_evaluation"
          ],
          "rootSectionAncestors":[
            "section_single_configuration_calculation"
          ]
        },
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"parsing_message_debug_evaluation",
          "description":"<p>A debugging message of the parsing program, associated with a single configuration calculation</p>",
          "gid":"pmQO2yHiZD_MUn0nj0rfRe4ggipG",
          "dtypeStr":"C (a unicode string)",
          "kindStr":"type_document_content",
          "superNames":[
            "parsing_message_debug",
            "section_single_configuration_calculation"
          ],
          "children":[

          ],
          "allparents":[
            "section_run",
            "section_single_configuration_calculation",
            "parsing_message_debug",
            "parsing_message_debug_evaluation"
          ],
          "rootSectionAncestors":[
            "section_single_configuration_calculation"
          ]
        },
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"electronic_kinetic_energy",
          "description":"<p>Electronic kinetic energy as defined in <a href=\"#/last/XC_method\"> XC_method </a> </p>",
          "gid":"pDfs446ST29BKUfIynXIG7fuGqNS",
          "units":"J",
          "dtypeStr":"f (floating point value)",
          "repeats":false,
          "kindStr":"type_document_content",
          "superNames":[
            "energy_component",
            "section_single_configuration_calculation"
          ],
          "children":[

          ],
          "allparents":[
            "section_run",
            "energy_value",
            "section_single_configuration_calculation",
            "energy_component",
            "electronic_kinetic_energy"
          ],
          "rootSectionAncestors":[
            "section_single_configuration_calculation"
          ],
          "shape":[

          ]
        },
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"energy_total_T0_per_atom",
          "description":"<p>Total energy using <a href=\"#/last/XC_method\"> XC_method </a> per atom extapolated for T=0</p>",
          "gid":"p5fWV9_3VBoqDkDvMPvB1hlWltYZ",
          "units":"J",
          "dtypeStr":"f (floating point value)",
          "repeats":false,
          "derived":true,
          "kindStr":"type_document_content",
          "superNames":[
            "energy_total_potential_per_atom",
            "section_single_configuration_calculation"
          ],
          "children":[

          ],
          "allparents":[
            "energy_value",
            "section_run",
            "energy_component",
            "section_single_configuration_calculation",
            "energy_total_potential_per_atom",
            "energy_total_T0_per_atom"
          ],
          "rootSectionAncestors":[
            "section_single_configuration_calculation"
          ],
          "shape":[

          ]
        },
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"section_energy_comparable",
          "description":"<p>A shifted total energy that should be more comparable among different codes, numerical settings, ... Details can be found on the <a href=\"https://gitlab.mpcdf.mpg.de/nomad-lab/public-wiki/wikis/metainfo/energy-comparable\">energy-comparable wiki page</a>.</p>",
          "gid":"pQKDNt9YODELOOIMFVDCIloT-aoH",
          "kindStr":"type_section",
          "superNames":[
            "section_single_configuration_calculation"
          ],
          "children":[
            "energy_comparable_kind",
            "energy_comparable_value"
          ],
          "allparents":[
            "section_run",
            "section_single_configuration_calculation",
            "section_energy_comparable"
          ],
          "rootSectionAncestors":[
            "section_single_configuration_calculation"
          ]
        },
        {
          "type":"nomad_meta_versions_1_0",
          "versions":"last",
          "name":"eigenvalues_kind",
          "description":"<p>A short string describing the kind of eigenvalues, as defined in the <a href=\"https://gitlab.mpcdf.mpg.de/nomad-lab/public-wiki/wikis/metainfo/eigenvalues-kind\">eigenvalues_kind wiki page</a>.</p>",
          "gid":"puVey2IKnwrIgQq2UShPpjQuQPlG",
          "dtypeStr":"C (a unicode string)",
          "kindStr":"type_document_content",
          "superNames":[
            "section_eigenvalues"
          ],
          "children":[

          ],
          "allparents":[
            "section_run",
            "section_single_configuration_calculation",
            "section_eigenvalues_group",
            "section_eigenvalues",
            "eigenvalues_kind"
          ],
          "rootSectionAncestors":[
            "section_eigenvalues"
          ],
          "shape":[

          ]
        }
      ];