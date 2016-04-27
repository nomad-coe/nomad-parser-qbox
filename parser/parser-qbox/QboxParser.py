import setup_paths
import numpy as np
import nomadcore.ActivateLogging
from nomadcore.caching_backend import CachingLevel
from nomadcore.simple_parser import AncillaryParser, mainFunction
from nomadcore.simple_parser import SimpleMatcher as SM
from QboxCommon import get_metaInfo
import logging, os, re, sys

############################################################
# This is the parser for the main file of dmol3.
############################################################


############################################################
###############[1] transfer PARSER CONTEXT #################
############################################################
logger = logging.getLogger("nomad.qboxParser") 

class QboxParserContext(object):

    def __init__(self):
        self.functionals                       = []

    def initialize_values(self):
        """Initializes the values of certain variables.

        This allows a consistent setting and resetting of the variables,
        when the parsing starts and when a section_run closes.
        """

    def startedParsing(self, fInName, parser):
        """Function is called when the parsing starts.

        Get compiled parser, filename and metadata.

        Args:
            fInName: The file name on which the current parser is running.
            parser: The compiled parser. Is an object of the class SimpleParser in nomadcore.simple_parser.py.
        """
        self.parser = parser
        self.fName = fInName
        # save metadata
        self.metaInfoEnv = self.parser.parserBuilder.metaInfoEnv
        # allows to reset values if the same superContext is used to parse different files
        self.initialize_values()



    ###################################################################
    # (2.1) onClose for INPUT geometry (section_system_description)
    ###################################################################
    def onClose_section_system_description(self, backend, gIndex, section):
        """Trigger called when section_system_description is closed.
        Writes atomic positions, atom labels and lattice vectors.
        """
        # keep track of the latest system description section
        self.secSystemDescriptionIndex = gIndex

        atom_pos = []
        for i in ['x', 'y', 'z']:
            api = section['dmol3_geometry_atom_position_' + i]
            if api is not None:
               atom_pos.append(api)
        if atom_pos:
            # need to transpose array since its shape is [number_of_atoms,3] in the metadata
           backend.addArrayValues('atom_position', np.transpose(np.asarray(atom_pos)))
            # write atom labels
        atom_labels = section['dmol3_geometry_atom_label']
        if atom_labels is not None:
           backend.addArrayValues('atom_label', np.asarray(atom_labels))
 
        #atom_hirshfeld_population_analysis = section['dmol3_hirshfeld_population']        
        #if atom_hirshfeld_population_analysis is not None:
        #   backend.addArrayValues('atom_hirshfeld_population',np.asarray(atom_hirshfeld_population_analysis))
        ###---???shanghui want to know how to add 

    #################################################################
    # (2.2) onClose for INPUT control (section_method)
    #################################################################
    def onClose_section_method(self, backend, gIndex, section):
        functional = section["dmol3_functional_name"]
        if functional:
            functionalMap = {
                "gga": ["GGA_X_PW91","GGA_C_PW91"]
            }
            # Push the functional string into the backend
            nomadNames = functionalMap.get(functional[0])
            if not nomadNames:
                raise Exception("Unhandled xc functional %s found" % functional)
            for name in nomadNames:
                s = backend.openSection("section_XC_functionals")
                backend.addValue('XC_functional_name', name)
                backend.closeSection("section_XC_functionals", s)


    # #################################################################
    # # (3.1) onClose for OUTPUT SCF (section_scf_iteration) 
    # #################################################################
    # # Storing the total energy of each SCF iteration in an array
    # def onClose_section_scf_iteration(self, backend, gIndex, section):
    #     """trigger called when _section_scf_iteration is closed"""
    #     # get cached values for energy_total_scf_iteration
    #     ev = section['energy_total_scf_iteration']
    #     self.scfIterNr = len(ev)
    # 
    #     #self.energy_total_scf_iteration_list.append(ev)
    #     #backend.addArrayValues('energy_total_scf_iteration_list', np.asarray(ev))
    #     #backend.addValue('scf_dft_number_of_iterations', self.scfIterNr)
    #     #-----???shanghui want to know why can not add them.
 

    #################################################################
    # (3.2) onClose for OUTPUT eigenvalues (section_eigenvalues) 
    #################################################################
    def onClose_section_eigenvalues(self, backend, gIndex, section):
        """Trigger called when _section_eigenvalues is closed.
        Eigenvalues are extracted.
        """
        occs = []
        evs =  []

        ev = section['dmol3_eigenvalue_eigenvalue']
        if ev is not None: 
           occ = section['dmol3_eigenvalue_occupation']
           occs.append(occ) 
           evs.append(ev)

        self.eigenvalues_occupation = []
        self.eigenvalues_eigenvalues = []

        #self.eigenvalues_kpoints = [] 
        self.eigenvalues_occupation.append(occs)
        self.eigenvalues_eigenvalues.append(evs)


                

#############################################################
#################[2] MAIN PARSER STARTS HERE  ###############
#############################################################

def build_QboxMainFileSimpleMatcher():
    """Builds the SimpleMatcher to parse the main file of dmol3.

    First, several subMatchers are defined, which are then used to piece together
    the final SimpleMatcher.
    SimpleMatchers are called with 'SM (' as this string has length 4,
    which allows nice formating of nested SimpleMatchers in python.

    Returns:
       SimpleMatcher that parses main file of dmol3. 
    """


    #####################################################################
    # (1) submatcher for header
    #####################################################################
    headerSubMatcher = SM(name = 'ProgramHeader',
                  startReStr = r"\s*I qbox\s+(?P<program_version>[0-9.]+)",
                  subMatchers = [
                     SM(r"\s*<nodename>\s+(?P<qbox_nodename>[a-zA-Z0-9]+)\s+</nodename>")
                                  ])

    #####################################################################
    # (2.1) submatcher for INPUT geometry(section_system_description)
    #####################################################################
    geometrySubMatcher = SM(name = 'Geometry',
        startReStr = r"\s*INCOOR, atomic coordinates in au \(for archive\):",
        sections = ['section_system_description'],
        subMatchers = [
       # SM (startReStr = r"\s*\|\s*Unit cell:",
       #     subMatchers = [
       #     SM (r"\s*\|\s*(?P<dmol3_geometry_lattice_vector_x__bohr>[-+0-9.]+)\s+(?P<dmol3_geometry_lattice_vector_y__bohr>[-+0-9.]+)\s+(?P<dmol3_geometry_lattice_vector_z__bohr>[-+0-9.]+)", repeats = True)
       #     ]),
        SM (startReStr = r"\s*\$coordinates",
            subMatchers = [
            SM (r"\s*(?P<dmol3_geometry_atom_label>[a-zA-Z]+)\s+(?P<dmol3_geometry_atom_position_x__bohr>[-+0-9.]+)\s+(?P<dmol3_geometry_atom_position_y__bohr>[-+0-9.]+)\s+(?P<dmol3_geometry_atom_position_z__bohr>[-+0-9.]+)", repeats = True)
            ])
        ])




    ####################################################################
    # (2.2) submatcher for INPUT control (section_method)
    ####################################################################
    calculationMethodSubMatcher = SM(name = 'calculationMethods',
        startReStr = r"\s*INPUT_DMOL keywords \(for archive\):",
        endReStr = r"\s*\>8",
        sections = ["section_method"],
        subMatchers = [
            SM(r"\s*Calculate\s+(?P<dmol3_calculation_type>[A-Za-z_]+)"),
            SM(r"\s*Functional\s+(?P<dmol3_functional_name>[A-Za-z0-9]+)"),
            SM(r"\s*Pseudopotential\s+(?P<dmol3_pseudopotential_name>[A-Za-z]+)"),
            SM(r"\s*Basis\s+(?P<dmol3_basis_name>[A-Za-z]+)"),
            SM(r"\s*Spin_Polarization\s+(?P<dmol3_spin_polarization>[A-Za-z]+)"),
            SM(r"\s*Spin\s+(?P<dmol3_spin>[0-9]+)"),
            SM(r"\s*Atom_Rcut\s+(?P<dmol3_rcut>[-+0-9.eEdD]+)"),
            SM(r"\s*Integration_Grid\s+(?P<dmol3_integration_grid>[A-Za-z]+)"),
            SM(r"\s*Aux_Partition\s+(?P<dmol3_aux_partition>[0-9]+)"),
            SM(r"\s*Aux_Density\s+(?P<dmol3_aux_density>[A-Za-z]+)"),

            SM(r"\s*Charge\s+(?P<dmol3_charge>[-+0-9.eEdD]+)"),
            SM(r"\s*Symmetry\s+(?P<dmol3_symmetry>[A-Za-z0-9]+)"),
            SM(r"\s*Mulliken_Analysis\s+(?P<dmol3_mulliken_analysis>[A-Za-z]+)"),
            SM(r"\s*Hirshfeld_Analysis\s+(?P<dmol3_hirshfeld_analysis>[A-Za-z]+)"),
            SM(r"\s*Partial_Dos\s+(?P<dmol3_partial_dos>[A-Za-z]+)"),
            SM(r"\s*Electrostatic_Moments\s+(?P<dmol3_electrostatic_moments>[A-Za-z]+)"),
            SM(r"\s*Nuclear_EFG\s+(?P<dmol3_nuclear_efg>[A-Za-z]+)"),
            SM(r"\s*Optical_Absorption\s+(?P<dmol3_optical_absorption>[A-Za-z]+)"),
            SM(r"\s*Kpoints\s+(?P<dmol3_kpoints>[A-Za-z]+)"),

            SM(r"\s*SCF_Density_Convergence\s+(?P<dmol3_scf_density_convergence>[-+0-9.eEdD]+)"),
            SM(r"\s*SCF_Spin_Mixing\s+(?P<dmol3_scf_spin_mixing>[-+0-9.eEdD]+)"),
            SM(r"\s*SCF_Charge_Mixing\s+(?P<dmol3_scf_charge_mixing>[-+0-9.eEdD]+)"),
            SM(r"\s*SCF_DIIS\s+(?P<dmol3_scf_diis_number>[-+0-9.eEdD]+)\s+(?P<dmol3_scf_diis_name>[A-Za-z]+)"),
            SM(r"\s*SCF_Iterations\s+(?P<dmol3_scf_iterations>[0-9]+)"),
            SM(r"\s*SCF_Number_Bad_Steps\s+(?P<dmol3_scf_number_bad_steps>[0-9]+)"),
            SM(r"\s*SCF_Direct\s+(?P<dmol3_scf_direct>[A-Za-z]+)"),
            SM(r"\s*SCF_Restart\s+(?P<dmol3_scf_restart>[A-Za-z]+)"),
            SM(r"\s*Occupation\s+(?P<dmol3_occupation_name>[A-Za-z_]+)\s+(?P<dmol3_occupation_width>[0-9.eEdD]+)"),

            SM(r"\s*OPT_Energy_Convergence\s+(?P<dmol3_opt_energy_convergence>[-+0-9.eEdD]+)"),
            SM(r"\s*OPT_Gradient_Convergence\s+(?P<dmol3_opt_gradient_convergence>[-+0-9.eEdD]+)"),
            SM(r"\s*OPT_Displacement_Convergence\s+(?P<dmol3_opt_displacement_convergence>[-+0-9.eEdD]+)"),
            SM(r"\s*OPT_Iterations\s+(?P<dmol3_opt_iterations>[0-9]+)"),
            SM(r"\s*OPT_Coordinate_System\s+(?P<dmol3_opt_coordinate_system>[A-Za-z_]+)"),
            SM(r"\s*OPT_Gdiis\s+(?P<dmol3_opt_gdiis>[A-Za-z]+)"),
            SM(r"\s*OPT_Max_Displacement\s+(?P<dmol3_opt_max_displacement>[-+0-9.eEdD]+)"),
            SM(r"\s*OPT_Steep_Tol\s+(?P<dmol3_opt_steep_tol>[-+0-9.eEdD]+)"),
            SM(r"\s*OPT_Hessian_Project\s+(?P<dmol3_opt_hessian_project>[A-Za-z]+)")


        ]) 

    ####################################################################
    # (3.1) submatcher for OUPUT SCF
    ####################################################################
    scfSubMatcher = SM(name = 'ScfIterations',
        startReStr = r"\s*Message: Start SCF iterations\s*",
        endReStr = r"\s*Message: SCF converged\s*",
        subMatchers = [
            SM(r"\s*Ef\s+(?P<energy_total_scf_iteration__hartree>[-+0-9.eEdD]+)\s+(?P<dmol3_binding_energy_scf_iteration__hartree>[-+0-9.eEdD]+)\s+(?P<dmol3_convergence_scf_iteration>[-+0-9.eEdD]+)\s+(?P<dmol3_time_scf_iteration>[0-9.eEdD]+)\s+(?P<dmol3_number_scf_iteration>[0-9]+)\s*",
               sections = ['section_scf_iteration'],
               repeats = True)

        ]) 
      
    ####################################################################
    # (3.2) submatcher for OUPUT eigenvalues
    ####################################################################
    eigenvalueSubMatcher = SM(name = 'Eigenvalues',
        startReStr = r"\s*state\s+eigenvalue\s+occupation\s*",
        sections = ['section_eigenvalues'],
        subMatchers = [
            SM(r"\s*[0-9]+\s+[+-]\s+[0-9]+\s+[A-Za-z]+\s+[-+0-9.eEdD]+\s+(?P<dmol3_eigenvalue_eigenvalue__eV>[-+0-9.eEdD]+)\s+(?P<dmol3_eigenvalue_occupation>[0-9.eEdD]+)", repeats = True)
        ]) 


    ####################################################################
    # (3.3) submatcher for OUPUT totalenergy
    ####################################################################
    totalenergySubMatcher = SM(name = 'Totalenergy',
        startReStr = r"\s*total_electronic_charge",
        subMatchers = [
            SM(r"\s*<etotal>\s+(?P<energy_total__hartree>[-+0-9.eEdD]+)\s+</etotal>",repeats = True ) 
        ]) 
    

    #####################################################################
    # (3.4) submatcher for OUTPUT relaxation_geometry(section_system_description)
    #####################################################################
    geometryrelaxationSubMatcher = SM(name = 'GeometryRelaxation',
        startReStr = r"\s*df\s*ATOMIC\s*COORDINATES\s*\(au\)\s*DERIVATIVES\s*\(au\)",
        #endReStr = r"\s*\+\+\+\s+Entering Vibrations Section\s+\+\+\+ ",
        sections = ['section_system_description'],
        subMatchers = [
        SM (startReStr = r"\s*df\s+x\s+y\s+z\s+x\s+y\s+z",
            subMatchers = [
            SM (r"\s*df\s+(?P<dmol3_geometry_atom_label>[a-zA-Z]+)\s+(?P<dmol3_geometry_atom_position_x__angstrom>[-+0-9.]+)\s+(?P<dmol3_geometry_atom_position_y__angstrom>[-+0-9.]+)\s+(?P<dmol3_geometry_atom_position_z__angstrom>[-+0-9.]+)\s+[-+0-9.]+\s+[-+0-9.]+\s+[-+0-9.]+", repeats = True)
            ])
        ])


    #####################################################################
    # (3.5) submatcher for OUTPUT population analysis (section_system_description)
    #####################################################################
    populationSubMatcher = SM(name = 'PopulationAnalysis',
        startReStr = r"\s*\+\+\+\s+Entering Properties Section\s+\+\+\+",
        subMatchers = [
        SM (startReStr = r"\s*Charge partitioning by Hirshfeld method:",
            sections = [ "dmol3_section_hirshfeld_population"],
            subMatchers = [
            SM (r"\s*[a-zA-Z]+\s+[0-9]+\s+charge\s+(?P<dmol3_hirshfeld_population>[-+0-9.]+)", repeats = True)
            ]),
        SM (startReStr = r"\s*Mulliken atomic charges:",
            sections = [ "dmol3_section_mulliken_population"],
            subMatchers = [
            SM (r"\s*[a-zA-Z(]+\s+[0-9)]+\s+(?P<dmol3_mulliken_population>[-+0-9.]+)", repeats = True)
            ])
        ])


    ########################################
    # return main Parser
    ########################################
    return SM (name = 'Root',

        startReStr = "",
        forwardMatch = True,
        weak = True,
        subMatchers = [
        SM (name = 'NewRun',
            startReStr = r"\s*============================",
            endReStr = r"\s*<end_time",
            repeats = False,
            required = True,
            forwardMatch = True,
            sections = ['section_run'],
            subMatchers = [

             #-----------(1) header--------------------- 
             headerSubMatcher,


             #----------(2.1) INPUT : geometry in *.r----------
             #geometrySubMatcher,  
             #----------(2.2) INPUT : method in *.i-----------
             #calculationMethodSubMatcher,  

            SM(name = "single configuration matcher",
                startReStr = r"\s*<iteration count*",
                #endReStr = r"\s*~~~~~~~~*\s*End Computing SCF Energy/Gradient\s*~~~~~~~~~~*",
                repeats = True,
                 sections = ['section_single_configuration_calculation'],
                 subMatchers = [
                    #----------(3.1) OUTPUT : SCF----------------------
                    #scfSubMatcher,
                    #----------(3.2) OUTPUT : eigenvalues--------------
                    #eigenvalueSubMatcher,
                    #----------(3.4) OUTPUT : relaxation_geometry----------------------
                    #geometryrelaxationSubMatcher,
                    ###---???shanghui find this will mismacth the frequencies geometry.
                    #----------(3.3) OUTPUT : totalenergy--------------
                    totalenergySubMatcher
                    ]
                ),




           ]) # CLOSING SM NewRun  

        ]) # END Root

def get_cachingLevelForMetaName(metaInfoEnv):
    """Sets the caching level for the metadata.

    Args:
        metaInfoEnv: metadata which is an object of the class InfoKindEnv in nomadcore.local_meta_info.py.

    Returns:
        Dictionary with metaname as key and caching level as value. 
    """
    # manually adjust caching of metadata
    cachingLevelForMetaName = {
                                'eigenvalues_eigenvalues': CachingLevel.Cache,
                                'eigenvalues_kpoints':CachingLevel.Cache
                                }

    # Set caching for temparary storage variables
    for name in metaInfoEnv.infoKinds:
        if (   name.startswith('qbox_store_')
            or name.startswith('qbox_cell_')):
            cachingLevelForMetaName[name] = CachingLevel.Cache
    return cachingLevelForMetaName




def main():
    """Main function.

    Set up everything for the parsing of the qbox main file and run the parsing.
    """
    # get main file description
    QboxMainFileSimpleMatcher = build_QboxMainFileSimpleMatcher()
    # loading metadata from nomad-meta-info/meta_info/nomad_meta_info/qbox.nomadmetainfo.json
    metaInfoPath = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../../nomad-meta-info/meta_info/nomad_meta_info/qbox.nomadmetainfo.json"))
    metaInfoEnv = get_metaInfo(metaInfoPath)
    # set parser info
    parserInfo = {'name':'qbox-parser', 'version': '1.0'}
    # get caching level for metadata
    cachingLevelForMetaName = get_cachingLevelForMetaName(metaInfoEnv)
    # start parsing
    mainFunction(mainFileDescription = QboxMainFileSimpleMatcher,
                 metaInfoEnv = metaInfoEnv,
                 parserInfo = parserInfo,
                 cachingLevelForMetaName = cachingLevelForMetaName,
                 superContext = QboxParserContext())

if __name__ == "__main__":
    main()

