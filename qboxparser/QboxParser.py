from builtins import object
import numpy as np
import nomadcore.ActivateLogging
from nomadcore.caching_backend import CachingLevel
from nomadcore.simple_parser import AncillaryParser, mainFunction
from nomadcore.simple_parser import SimpleMatcher as SM
from .QboxCommon import get_metaInfo
from . import QboxXMLParser
import logging, os, re, sys

############################################################
# This is the parser for the main file of qbox (for the output file *.r) .
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


    #################################################################
    # (2) onClose for INPUT control (section_method)
    #################################################################
    def onClose_x_qbox_section_xml_file(self, backend, gIndex, section):

        x_qbox_loading_xml_file_list = section['x_qbox_loading_xml_file']

        xml_file = x_qbox_loading_xml_file_list[-1]

        if xml_file is not None:
           logger.warning("This output showed this calculation need to load xml file, so we need this xml file ('%s') to read geometry information" % os.path.normpath(xml_file) )
           fName = os.path.normpath(xml_file)

           xmlSuperContext = QboxXMLParser.QboxXMLParserContext(False)
           xmlParser = AncillaryParser(
                fileDescription = QboxXMLParser.build_QboxXMLFileSimpleMatcher(),
                parser = self.parser,
                cachingLevelForMetaName = QboxXMLParser.get_cachingLevelForMetaName(self.metaInfoEnv, CachingLevel.Ignore),
                superContext = xmlSuperContext)

           try:
                with open(fName) as fxml:
                     xmlParser.parseFile(fxml)

           except IOError:
                logger.warning("Could not find xml file in directory '%s'. " % os.path.dirname(os.path.abspath(self.fName)))


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
    #     #backend.addValue('number_of_scf_iterations', self.scfIterNr)
    #     #-----???shanghui want to know why can not add them.


    #################################################################
    # (3.2) onClose for OUTPUT eigenvalues (section_eigenvalues)
    #################################################################
    #def onClose_section_eigenvalues(self, backend, gIndex, section):
    #    """Trigger called when _section_eigenvalues is closed.
    #    Eigenvalues are extracted.
    #    """
    #    occs = []
    #    evs =  []

    #    ev = section['qbox_eigenvalue_eigenvalue']
    #    if ev is not None:
    #       occ = section['qbox_eigenvalue_occupation']
    #       occs.append(occ)
    #       evs.append(ev)

    #    self.eigenvalues_occupation = []
    #    self.eigenvalues_values = []

    #    #self.eigenvalues_kpoints = []
    #    self.eigenvalues_occupation.append(occs)
    #    self.eigenvalues_values.append(evs)


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

        #------3.atom force
        atom_force = []
        for i in ['x', 'y', 'z']:
            api = section['x_qbox_atom_force_' + i]
            if api is not None:
               atom_force.append(api)
        if atom_force:
            # need to transpose array since its shape is [number_of_atoms,3] in the metadata
           backend.addArrayValues('atom_forces', np.transpose(np.asarray(atom_force)))


        #----4. unit_cell
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


    def onClose_x_qbox_section_stress_tensor(self, backend, gIndex, section):
        x_qbox_stress_tensor = []
        for i in ['xx', 'yy', 'zz', 'xy', 'yz', 'xz']:
            api = section['x_qbox_stress_tensor_' + i]
            if api is not None:
               x_qbox_stress_tensor.append(api)
        if x_qbox_stress_tensor:
            # need to transpose array since its shape is [number_of_atoms,3] in the metadata
           backend.addArrayValues('stress_tensor', np.transpose(np.asarray(x_qbox_stress_tensor)))







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
    # (1) submatcher for header
    #note: we add x_qbox_section_functionals here because we want to add
    #      a default LDA here even 'set xc' is not shown in *.i file.
    #####################################################################
    headerSubMatcher = SM(name = 'ProgramHeader',
                  startReStr = r"\s*I qbox\s+(?P<program_version>[0-9.]+)",
                #   sections = ["x_qbox_section_functionals"],
                  subMatchers = [
                     SM(r"\s*<nodename>\s+(?P<x_qbox_nodename>[a-zA-Z0-9.-]+)\s+</nodename>")
                                  ])

    ####################################################################
    # (2.2) submatcher for properties : efield  (qbox_section_efield)
    ####################################################################
    #efieldSubMatcher = SM(name = 'Efield',
    #    startReStr = r"\s*\[qbox\]\s*\[qbox\]\s*<cmd>set\s*e_field",
    #    sections = ["qbox_section_efield"],
    #    subMatchers = [
    #      SM (r"\s*<e_field>\s+(?P<qbox_efield_x>[-+0-9.]+)\s+(?P<qbox_efield_y>[-+0-9.]+)\s+(?P<qbox_efield_z>[-+0-9.]+)\s*</e_field>",repeats = True)
    #    ])


    ####################################################################
    # (2) submatcher for control method that echo INPUT file (section_method)
    ####################################################################
    calculationMethodSubMatcher = SM(name = 'calculationMethods',
        startReStr = r"\s*\[qbox\]",
        #endReStr = r"\s*",
        repeats = True,
        sections = ["section_method"],
        subMatchers = [

           #-----load from xml file-----
           # when using 'load *.xml' in *.i file, qbox will read geometry information from xml file,
           # here we call a QboxXMLParser to read the geometry information out.
           SM(name = "qboxXMLfile",
             startReStr = r"\s*LoadCmd",
             forwardMatch = True, #use this or not like qboxXC
             sections = ["x_qbox_section_xml_file"],
             subMatchers = [
             SM(r"\s*LoadCmd:\s*loading from\s+(?P<x_qbox_loading_xml_file>[A-Za-z0-9./-_]+)"),
                ]), # CLOSING qbox_section_xml_file

        #--------k_point-------------
            SM(r"\s*\[qbox\]\s+<cmd>\s*kpoint add\s+(?P<x_qbox_k_point_x>[-+0-9.eEdD]+)\s+(?P<x_qbox_k_point_y>[-+0-9.eEdD]+)\s+(?P<x_qbox_k_point_z>[-+0-9.eEdD]+)\s+(?P<x_qbox_k_point_weight>[-+0-9.eEdD]+)\s*</cmd>",repeats = True),

        #--------set method---------
            SM(r"\s*\[qbox\]\s*\[qbox\]\s*<cmd>\s*set\s+ecut\s+(?P<x_qbox_ecut__rydberg>[0-9.]+)\s*</cmd>"),
            SM(r"\s*\[qbox\]\s+<cmd>\s*set\s+wf_dyn\s+(?P<x_qbox_wf_dyn>[A-Za-z0-9]+)\s*</cmd>"),
            SM(r"\s*\[qbox\]\s+<cmd>\s*set\s+atoms_dyn\s+(?P<x_qbox_atoms_dyn>[A-Za-z0-9]+)\s*</cmd>"),
            SM(r"\s*\[qbox\]\s+<cmd>\s*set\s+cell_dyn\s+(?P<x_qbox_cell_dyn>[A-Za-z0-9]+)\s*</cmd>"),

        #--------set xc---------
            SM(name = "qboxXC",
              startReStr = r"\s*\[qbox\]\s+<cmd>\s*set\s+xc\s+(?P<x_qbox_functional_name>[A-Za-z0-9]+)\s*</cmd>",
              sections = ["x_qbox_section_functionals"]
               ),

        #-------set efield---------
            SM (r"\s*\[qbox\]\s*\[qbox\]\s*<cmd>\s*set\s+e_field\s*(?P<x_qbox_efield_x>[-+0-9.]+)\s+(?P<x_qbox_efield_y>[-+0-9.]+)\s+(?P<x_qbox_efield_z>[-+0-9.]+)\s*</cmd>",repeats = True)
          #???both this version adn qbox_section_efield version could not give mather for efield, need to check.
        ])

    ####################################################################
    # (3.1) submatcher for OUPUT SCF, this Dmol parser, we need to change to qbox later
    ####################################################################
#    scfSubMatcher = SM(name = 'ScfIterations',
#        startReStr = r"\s*Message: Start SCF iterations\s*",
#        endReStr = r"\s*Message: SCF converged\s*",
#        subMatchers = [
#            SM(r"\s*Ef\s+(?P<energy_total_scf_iteration__hartree>[-+0-9.eEdD]+)\s+(?P<dmol3_binding_energy_scf_iteration__hartree>[-+0-9.eEdD]+)\s+(?P<dmol3_convergence_scf_iteration>[-+0-9.eEdD]+)\s+(?P<dmol3_time_scf_iteration>[0-9.eEdD]+)\s+(?P<dmol3_number_scf_iteration>[0-9]+)\s*",
#               sections = ['section_scf_iteration'],
#               repeats = True)
#
#        ])

    ####################################################################
    # (3.2) submatcher for OUPUT eigenvalues,  this Dmol parser, we need to change to qbox later
    ####################################################################
#   eigenvalueSubMatcher = SM(name = 'Eigenvalues',
#       startReStr = r"\s*state\s+eigenvalue\s+occupation\s*",
#       sections = ['section_eigenvalues'],
#       subMatchers = [
#           SM(r"\s*[0-9]+\s+[+-]\s+[0-9]+\s+[A-Za-z]+\s+[-+0-9.eEdD]+\s+(?P<dmol3_eigenvalue_e igenvalue__eV>[-+0-9.eEdD]+)\s+(?P<dmol3_eigenvalue_occupation>[0-9.eEdD]+)", repeats = True)
#       ])
#

    ####################################################################
    # (3.3) submatcher for OUPUT totalenergy
    ####################################################################
    totalenergySubMatcher = SM(name = 'Totalenergy',
        startReStr = r"\s*<ekin>",
        subMatchers = [
            SM(r"\s*<etotal>\s+(?P<energy_total__hartree>[-+0-9.eEdD]+)\s+</etotal>",repeats = True )
        ])


    #####################################################################
    # (3.4) submatcher for OUTPUT relaxation_geometry(section_system)
    #####################################################################
    geometryrelaxationSubMatcher = SM(name = 'GeometryRelaxation',
        startReStr = r"\s*<atomset>",
        sections = ['section_system'],
        subMatchers = [
        SM (startReStr = r"\s*<unit_cell\s*",
            subMatchers = [
            SM (r"\s*[a-z]=\"\s*(?P<x_qbox_geometry_lattice_vector_x__bohr>[-+0-9.]+)\s+(?P<x_qbox_geometry_lattice_vector_y__bohr>[-+0-9.]+)\s+(?P<x_qbox_geometry_lattice_vector_z__bohr>[-+0-9.]+)\s*\"", repeats = True)
            ]),
        SM (startReStr = r"\s*<atom\s+name=\"(?P<x_qbox_geometry_atom_labels>[a-zA-Z0-9]+)\"",
            subMatchers = [
            SM (r"\s*<position>\s+(?P<x_qbox_geometry_atom_positions_x__bohr>[-+0-9.]+)\s+(?P<x_qbox_geometry_atom_positions_y__bohr>[-+0-9.]+)\s+(?P<x_qbox_geometry_atom_positions_z__bohr>[-+0-9.]+)\s*</position>", repeats = True),
            SM (r"\s*<force>\s+(?P<x_qbox_atom_force_x__hartree_bohr_1>[-+0-9.]+)\s+(?P<x_qbox_atom_force_y__hartree_bohr_1>[-+0-9.]+)\s+(?P<x_qbox_atom_force_z__hartree_bohr_1>[-+0-9.]+)\s*</force>", repeats = True)
            ], repeats = True)
        ])


    #####################################################################
    # (3.5) submatcher for OUTPUT stress_tensor(x_qbox_section_stress_tensor)
    #####################################################################
    stresstensorSubMatcher = SM(name = 'StressTensor',
        startReStr = r"\s*<stress_tensor\s*unit=\"GPa\">",
        sections = ['x_qbox_section_stress_tensor'],
        subMatchers = [
          SM (r"\s*<sigma_xx>\s+(?P<x_qbox_stress_tensor_xx__GPa>[-+0-9.]+)\s*</sigma_xx>"),
          SM (r"\s*<sigma_yy>\s+(?P<x_qbox_stress_tensor_yy__GPa>[-+0-9.]+)\s*</sigma_yy>"),
          SM (r"\s*<sigma_zz>\s+(?P<x_qbox_stress_tensor_zz__GPa>[-+0-9.]+)\s*</sigma_zz>"),
          SM (r"\s*<sigma_xy>\s+(?P<x_qbox_stress_tensor_xy__GPa>[-+0-9.]+)\s*</sigma_xy>"),
          SM (r"\s*<sigma_yz>\s+(?P<x_qbox_stress_tensor_yz__GPa>[-+0-9.]+)\s*</sigma_yz>"),
          SM (r"\s*<sigma_xz>\s+(?P<x_qbox_stress_tensor_xz__GPa>[-+0-9.]+)\s*</sigma_xz>")
        ])

    ####################################################################
    # (4.1) submatcher for properties : MLWF  (x_qbox_section_MLWF)
    ####################################################################
    MLWFSubMatcher = SM(name = 'MLWF',
        startReStr = r"\s*<mlwfs>",
        sections = ["x_qbox_section_MLWF"],
        subMatchers = [
          SM (r"\s*<mlwf center=\"\s*(?P<x_qbox_geometry_MLWF_atom_positions_x__bohr>[-+0-9.]+)\s+(?P<x_qbox_geometry_MLWF_atom_positions_y__bohr>[-+0-9.]+)\s+(?P<x_qbox_geometry_MLWF_atom_positions_z__bohr>[-+0-9.]+)\s*\"\s+spread=\"\s*(?P<x_qbox_geometry_MLWF_atom_spread__bohr>[-+0-9.]+)\s*\"", repeats = True)

        ])


    ####################################################################
    # (4.2) submatcher for properties : dipole  (x_qbox_section_dipole)
    ####################################################################
    dipoleSubMatcher = SM(name = 'Dipole',
        startReStr = r"\s*<dipole>",
        sections = ["x_qbox_section_dipole"],
        subMatchers = [
          SM (r"\s*<dipole_total>\s+(?P<x_qbox_dipole_x>[-+0-9.]+)\s+(?P<x_qbox_dipole_y>[-+0-9.]+)\s+(?P<x_qbox_dipole_z>[-+0-9.]+)\s*</dipole_total>", repeats = True)
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
            startReStr = r"\s*============================",
            endReStr = r"\s*<end_time",
            repeats = False,
            required = True,
            forwardMatch = True,
            fixedStartValues={'program_name': 'qbox', 'program_basis_set_type': 'plane waves'},
            sections = ['section_run'],
            subMatchers = [
             #-----------(1)output: header---------------------
             headerSubMatcher,

             #-----------(2)output: method---------------------
             calculationMethodSubMatcher,
             #-----------(2.2) efield----------------------
             # efieldSubMatcher,

             #-----------(3)output: single configuration-------
             SM(name = "single configuration matcher",
                startReStr = r"\s*<iteration count*",
                #endReStr = r"\s*</iteration>",
                repeats = True,
                sections = ['section_single_configuration_calculation'],
                subMatchers = [
                    #----------(3.1) OUTPUT : SCF----------------------
                    #scfSubMatcher,
                    #----------(3.2) OUTPUT : eigenvalues--------------
                    #eigenvalueSubMatcher,
                    #----------(3.3) OUTPUT : totalenergy--------------
                    totalenergySubMatcher,
                    #----------(3.4) OUTPUT : relaxation_geometry----------------------
                    geometryrelaxationSubMatcher,
                    #----------(3.5) OUTPUT : stress----------------------
                    stresstensorSubMatcher
                    ]
                ),

             #-----------(4)output: properties-------
             #-----------(4.1) MLWF----------------------
              MLWFSubMatcher,
             #-----------(4.3) dipole----------------------
              dipoleSubMatcher

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


# get main file description
QboxMainFileSimpleMatcher = build_QboxMainFileSimpleMatcher()


class QboxParser():
    """ A proper class envolop for running this parser from within python. """
    def __init__(self, backend, **kwargs):
        self.backend_factory = backend

    def parse(self, mainfile):
        from unittest.mock import patch
        logging.debug('qbox parser started')
        logging.getLogger('nomadcore').setLevel(logging.WARNING)
        backend = self.backend_factory("qbox.nomadmetainfo.json")
        with patch.object(sys, 'argv', ['<exe>', '--uri', 'nmd://uri', mainfile]):
            mainFunction(
                mainFileDescription=QboxMainFileSimpleMatcher,
                metaInfoEnv=None,
                parserInfo={'name':'qbox-parser', 'version': '1.0'},
                cachingLevelForMetaName=get_cachingLevelForMetaName(backend.metaInfoEnv()),
                superContext=QboxParserContext(),
                superBackend=backend)

        return backend
