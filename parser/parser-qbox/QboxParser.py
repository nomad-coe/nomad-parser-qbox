import setup_paths
import numpy as np
import nomadcore.ActivateLogging
from nomadcore.caching_backend import CachingLevel
from nomadcore.simple_parser import AncillaryParser, mainFunction
from nomadcore.simple_parser import SimpleMatcher as SM
from QboxCommon import get_metaInfo
import logging, os, re, sys

############################################################
# This is the parser for the main file of qbox.
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
            api = section['qbox_geometry_atom_position_' + i]
            if api is not None:
               atom_pos.append(api)
        if atom_pos:
            # need to transpose array since its shape is [number_of_atoms,3] in the metadata
           backend.addArrayValues('atom_position', np.transpose(np.asarray(atom_pos)))
            # write atom labels
        atom_labels = section['qbox_geometry_atom_label']
        if atom_labels is not None:
           backend.addArrayValues('atom_label', np.asarray(atom_labels))
 
        #atom_hirshfeld_population_analysis = section['dmol3_hirshfeld_population']        
        #if atom_hirshfeld_population_analysis is not None:
        #   backend.addArrayValues('atom_hirshfeld_population',np.asarray(atom_hirshfeld_population_analysis))
        ###---???shanghui want to know how to add 

    #################################################################
    # (2.2) onClose for INPUT control (section_method)
    #################################################################
    #def onClose_section_method(self, backend, gIndex, section):
    #    functional = section["dmol3_functional_name"]
    #    if functional:
    #        functionalMap = {
    #            "gga": ["GGA_X_PW91","GGA_C_PW91"]
    #        }
    #        # Push the functional string into the backend
    #        nomadNames = functionalMap.get(functional[0])
    #        if not nomadNames:
    #            raise Exception("Unhandled xc functional %s found" % functional)
    #        for name in nomadNames:
    #            s = backend.openSection("section_XC_functionals")
    #            backend.addValue('XC_functional_name', name)
    #            backend.closeSection("section_XC_functionals", s)


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
    """Builds the SimpleMatcher to parse the main file of qbox.

    First, several subMatchers are defined, which are then used to piece together
    the final SimpleMatcher.
    SimpleMatchers are called with 'SM (' as this string has length 4,
    which allows nice formating of nested SimpleMatchers in python.

    Returns:
       SimpleMatcher that parses main file of qbox. 
    """


    #####################################################################
    # (1.1) submatcher for INPUT geometry(section_system_description)
    #####################################################################
    geometrySubMatcher = SM(name = 'Geometry',
        startReStr = r"",
        forwardMatch = True,
        required = True,
        sections = ['section_system_description'],
        subMatchers = [
        #SM (r"\s*set\s+cell(?P<qbox_geometry_lattice_vector_x__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_y__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_z__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_x__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_y__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_z__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_x__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_y__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_z__bohr>[-+0-9.]+)", repeats = True),
         SM (r"\s*atom\s+(?P<qbox_geometry_atom_label>[a-zA-Z1-9]+)\s+[a-z]+\s+(?P<qbox_geometry_atom_position_x__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_atom_position_y__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_atom_position_z__bohr>[-+0-9.]+)", repeats = True)
        ])




    ####################################################################
    # (1.2) submatcher for INPUT control (section_method)
    ####################################################################
    calculationMethodSubMatcher = SM(name = 'calculationMethods',
        startReStr = r"",
        #endReStr = r"\s*",
        forwardMatch = False,
        required = True,
        sections = ["section_method"],
        subMatchers = [
            SM(r"\s*set\s+ecut\s+(?P<qbox_ecut__rydberg>[0-9.]+)"),
            SM(r"\s*set\s+wf_dyn\s+(?P<qbox_wf_dyn>[A-Za-z0-9]+)")

        ]) 

    #####################################################################
    # (2.1) submatcher for header
    #####################################################################
    headerSubMatcher = SM(name = 'ProgramHeader',
                  startReStr = r"\s*I qbox\s+(?P<program_version>[0-9.]+)",
                  subMatchers = [
                     SM(r"\s*<nodename>\s+(?P<qbox_nodename>[a-zA-Z0-9.-]+)\s+</nodename>")
                                  ])

    ####################################################################
    # (2.2.1) submatcher for OUPUT SCF
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
    # (2.2.2) submatcher for OUPUT eigenvalues
    ####################################################################
    eigenvalueSubMatcher = SM(name = 'Eigenvalues',
        startReStr = r"\s*state\s+eigenvalue\s+occupation\s*",
        sections = ['section_eigenvalues'],
        subMatchers = [
            SM(r"\s*[0-9]+\s+[+-]\s+[0-9]+\s+[A-Za-z]+\s+[-+0-9.eEdD]+\s+(?P<dmol3_eigenvalue_eigenvalue__eV>[-+0-9.eEdD]+)\s+(?P<dmol3_eigenvalue_occupation>[0-9.eEdD]+)", repeats = True)
        ]) 


    ####################################################################
    # (2.2.3) submatcher for OUPUT totalenergy
    ####################################################################
    totalenergySubMatcher = SM(name = 'Totalenergy',
        startReStr = r"\s*total_electronic_charge",
        subMatchers = [
            SM(r"\s*<etotal>\s+(?P<energy_total__hartree>[-+0-9.eEdD]+)\s+</etotal>",repeats = True ) 
        ]) 
    

    #####################################################################
    # (2.2.4) submatcher for OUTPUT relaxation_geometry(section_system_description)
    #####################################################################
    geometryrelaxationSubMatcher = SM(name = 'GeometryRelaxation',
        startReStr = r"\s*<atomset>",
        sections = ['section_system_description'],
        subMatchers = [
        SM (startReStr = r"\s*<unit_cell\s*",
            subMatchers = [
            SM (r"\s*[a-z]=\"\s*(?P<qbox_geometry_lattice_vector_x__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_y__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_lattice_vector_z__bohr>[-+0-9.]+)\s*\"", repeats = True)
            ]),
        SM (startReStr = r"\s*<atom\s+name=\"(?P<qbox_geometry_atom_label>[a-zA-Z0-9]+)\"",
            subMatchers = [
            SM (r"\s*<position>\s+(?P<qbox_geometry_atom_position_x__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_atom_position_y__bohr>[-+0-9.]+)\s+(?P<qbox_geometry_atom_position_z__bohr>[-+0-9.]+)\s+</position>", repeats = True)
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

        #=================(1) read INPUT file *.i=======================
        #----------(1.1) geometry ----------
        #geometrySubMatcher, ???shanghui, why I would not use this when I use method together. 
        #----------(1.2)  method -----------
        calculationMethodSubMatcher,  


        #=================(2) read OUPUT file *.r=======================
        SM (name = 'NewRun',
            startReStr = r"\s*============================",
            endReStr = r"\s*<end_time",
            repeats = False,
            required = True,
            forwardMatch = True,
            sections = ['section_run'],
            subMatchers = [
             #-----------(2.1)output: header--------------------- 
             headerSubMatcher,

             #-----------(2.2)output: single configuration------- 
             SM(name = "single configuration matcher",
                startReStr = r"\s*<iteration count*",
                #endReStr = r"\s*~~~~~~~~*\s*End Computing SCF Energy/Gradient\s*~~~~~~~~~~*",
                repeats = True,
                sections = ['section_single_configuration_calculation'],
                subMatchers = [
                    #----------(2.2.1) OUTPUT : SCF----------------------
                    #scfSubMatcher,
                    #----------(2.2.2) OUTPUT : eigenvalues--------------
                    #eigenvalueSubMatcher,
                    #----------(2.2.3) OUTPUT : totalenergy--------------
                    totalenergySubMatcher,
                    #----------(2.2.4) OUTPUT : relaxation_geometry----------------------
                    geometryrelaxationSubMatcher
                    ]
                ),

             #-----------(2.3)output: properties------- 


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

