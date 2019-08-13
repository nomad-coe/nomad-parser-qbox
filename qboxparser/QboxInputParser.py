from builtins import object
import numpy as np
import nomadcore.ActivateLogging
from nomadcore.caching_backend import CachingLevel
from nomadcore.simple_parser import AncillaryParser, mainFunction
from nomadcore.simple_parser import SimpleMatcher as SM
from QboxCommon import get_metaInfo
import logging, os, re, sys

############################################################
# This is the parser for the input file of qbox.
############################################################


############################################################
###############[1] transfer PARSER CONTEXT #################
############################################################
logger = logging.getLogger("nomad.qboxInputParser")

class QboxInputParserContext(object):

    def __init__(self):
        self.functionals                       = []


    def initialize_values(self):
        """Initializes the values of certain variables.

        This allows a consistent setting and resetting of the variables,
        when the parsing starts and when a section_run closes.
        """
        self.secMethodIndex = None
        self.secSystemDescriptionIndex = None

        self.singleConfCalcs = []


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


    def onClose_section_run(self, backend, gIndex, section):
        """Trigger called when section_run is closed.
        """
        # reset all variables
        self.initialize_values()
        # frame sequence
        sampling_method = "geometry_optimization"

        samplingGIndex = backend.openSection("section_sampling_method")
        backend.addValue("sampling_method", sampling_method)
        backend.closeSection("section_sampling_method", samplingGIndex)
        frameSequenceGIndex = backend.openSection("section_frame_sequence")
        backend.addValue("frame_sequence_to_sampling_ref", samplingGIndex)
        backend.addArrayValues("frame_sequence_local_frames_ref", np.asarray(self.singleConfCalcs))
        backend.closeSection("section_frame_sequence", frameSequenceGIndex)



    def onClose_x_qbox_section_functionals(self, backend, gIndex, section):
        functional_list = section["x_qbox_functional_name"]

        if not functional_list: # default is LDA in qbox
           functional = "LDA"
        else :
           functional = functional_list[-1] # use the xc appeared the last time


        if functional:
            functionalMap = {
                "LDA": ["LDA_X", "LDA_C_PZ"],
                "VMN": ["LDA_X", "LDA_C_VWN"],
                "PBE": ["GGA_X_PBE","GGA_C_PBE"],
                "PBE0": ["GGA_X_PBE","GGA_C_PBE"],
                "B3LYP": ["HYB_GGA_XC_B3LYP5"]
     #need to be extended to add alpha_PBE0 :coefficient of Hartree-Fock exchange in the PBE0 xc functional
            }
            # Push the functional string into the backend
            nomadNames = functionalMap.get(functional)
            if not nomadNames:
                raise Exception("Unhandled xc functional %s found" % functional)
            for name in nomadNames:
                s = backend.openSection("section_XC_functionals")
                backend.addValue('XC_functional_name', name)
                backend.closeSection("section_XC_functionals", s)




    ###################################################################
    # (3.4) onClose for geometry and force (section_system)
    # todo: maybe we can move the force to onClose_section_single_configuration_calculation in the future.
    ###################################################################
    def onOpen_section_method(self, backend, gIndex, section):
        # keep track of the latest method section
        self.secMethodIndex = gIndex


    def onOpen_section_system(self, backend, gIndex, section):
        # keep track of the latest system description section
        self.secSystemDescriptionIndex = gIndex


    def onClose_section_system(self, backend, gIndex, section):
        """Trigger called when section_system is closed.
        Writes atomic positions, atom labels and lattice vectors.
        """
        # keep track of the latest system description section
        self.secSystemDescriptionIndex = gIndex

       #------1.atom_positions
        atom_pos = []
        for i in ['x', 'y', 'z']:
            api = section['x_qbox_geometry_atom_positions_' + i]
            if api is not None:
               atom_pos.append(api)
        if atom_pos:
            # need to transpose array since its shape is [number_of_atoms,3] in the metadata
           backend.addArrayValues('atom_positions', np.transpose(np.asarray(atom_pos)))

        #------2.atom labels
        atom_labels = section['x_qbox_geometry_atom_labels']
        if atom_labels is not None:
           backend.addArrayValues('atom_labels', np.asarray(atom_labels))



        #----3. unit_cell
        unit_cell = []
        for i in ['x', 'y', 'z']:
            uci = section['x_qbox_geometry_lattice_vector_' + i]
            if uci is not None:
                unit_cell.append(uci)
        if unit_cell:
           backend.addArrayValues('simulation_cell', np.asarray(unit_cell))
           backend.addArrayValues("configuration_periodic_dimensions", np.ones(3, dtype=bool))




    def onOpen_section_single_configuration_calculation(self, backend, gIndex, section):
        self.singleConfCalcs.append(gIndex)

    def onClose_section_single_configuration_calculation(self, backend, gIndex, section):
# write the references to section_method and section_system
        backend.addValue('single_configuration_to_calculation_method_ref', self.secMethodIndex)
        backend.addValue('single_configuration_calculation_to_system_ref', self.secSystemDescriptionIndex)






#############################################################
#################[2] MAIN PARSER STARTS HERE  ###############
#############################################################

def build_QboxInputFileSimpleMatcher():
    """Builds the SimpleMatcher to parse the input file of qbox (*.i) .

    First, several subMatchers are defined, which are then used to piece together
    the final SimpleMatcher.
    SimpleMatchers are called with 'SM (' as this string has length 4,
    which allows nice formating of nested SimpleMatchers in python.

    Returns:
       SimpleMatcher that parses input file of qbox.
    """




    ####################################################################
    # (2) submatcher for control method that echo INPUT file (section_method)
    ####################################################################
    calculationMethodSubMatcher = SM(name = 'calculationMethods',
        startReStr = "",
        #endReStr = r"\s*",
        repeats = True,
        sections = ["section_method"],
        subMatchers = [


        #--------k_point-------------
            SM(r"\s*kpoint add\s+(?P<x_qbox_k_point_x>[-+0-9.eEdD]+)\s+(?P<x_qbox_k_point_y>[-+0-9.eEdD]+)\s+(?P<x_qbox_k_point_z>[-+0-9.eEdD]+)\s+(?P<x_qbox_k_point_weight>[-+0-9.eEdD]+)\s*",repeats = True),

        #--------set method---------
            SM(r"\s*set\s+ecut\s+(?P<x_qbox_ecut__rydberg>[0-9.]+)\s*"),
            SM(r"\s*set\s+wf_dyn\s+(?P<x_qbox_wf_dyn>[A-Za-z0-9]+)\s*"),
            SM(r"\s*set\s+atoms_dyn\s+(?P<x_qbox_atoms_dyn>[A-Za-z0-9]+)\s*"),
            SM(r"\s*set\s+cell_dyn\s+(?P<x_qbox_cell_dyn>[A-Za-z0-9]+)\s*"),

        #--------set xc---------
            SM(name = "qboxXC",
              startReStr = r"\s*set\s+xc\s+(?P<x_qbox_functional_name>[A-Za-z0-9]+)\s*",
              sections = ["x_qbox_section_functionals"]
               ),

        #-------set efield---------
            SM (r"\s*set\s+e_field\s*(?P<x_qbox_efield_x>[-+0-9.]+)\s+(?P<x_qbox_efield_y>[-+0-9.]+)\s+(?P<x_qbox_efield_z>[-+0-9.]+)\s*",repeats = True)
          #???both this version adn qbox_section_efield version could not give mather for efield, need to check.
        ])






    ########################################
    # return main Parser
    ########################################
    return SM (name = 'Root',

        startReStr = "",
        forwardMatch = True,
        weak = True,
        subMatchers = [

        #=============================================================================
        #  read OUPUT file *.r, the method part comes from INPUT file *.i,  so we
        #  do not need to parser INPUT file, the OUTPUT file contains all information
        #=============================================================================
        SM (name = 'NewRun',
            startReStr = r"",
            #endReStr = r"\s*<end_time",
            repeats = False,
            required = True,
            forwardMatch = True,
            fixedStartValues={'program_name': 'qbox', 'program_basis_set_type': 'plane waves'},
            sections = ['section_run'],
            subMatchers = [

             #-----------input  method---------------------
             calculationMethodSubMatcher

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
                                'eigenvalues_values': CachingLevel.Cache,
                                'eigenvalues_kpoints':CachingLevel.Cache
                                }

    # Set caching for temparary storage variables
    for name in metaInfoEnv.infoKinds:
        if (   name.startswith('x_qbox_store_')
            or name.startswith('x_qbox_cell_')):
            cachingLevelForMetaName[name] = CachingLevel.Cache
    return cachingLevelForMetaName




def main():
    """Main function.

    Set up everything for the parsing of the qbox main file and run the parsing.
    """
    # get main file description
    QboxInputFileSimpleMatcher = build_QboxInputFileSimpleMatcher()
    # loading metadata from nomad-meta-info/meta_info/nomad_meta_info/qbox.nomadmetainfo.json
    metaInfoPath = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../../nomad-meta-info/meta_info/nomad_meta_info/qbox.nomadmetainfo.json"))
    metaInfoEnv = get_metaInfo(metaInfoPath)
    # set parser info
    parserInfo = {'name':'qbox-input-parser', 'version': '1.0'}
    # get caching level for metadata
    cachingLevelForMetaName = get_cachingLevelForMetaName(metaInfoEnv)
    # start parsing
    mainFunction(mainFileDescription = QboxInputFileSimpleMatcher,
                 metaInfoEnv = metaInfoEnv,
                 parserInfo = parserInfo,
                 cachingLevelForMetaName = cachingLevelForMetaName,
                 superContext = QboxInputParserContext())

if __name__ == "__main__":
    main()

