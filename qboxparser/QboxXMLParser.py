from builtins import object
import numpy as np
import nomadcore.ActivateLogging
from nomadcore.caching_backend import CachingLevel
from nomadcore.simple_parser import mainFunction
from nomadcore.simple_parser import SimpleMatcher as SM
from .QboxCommon import get_metaInfo
import logging, os, re, sys



#################################################################################
# This is the parser for the *.xml file of qbox to read the geometry information.
# Only needed when 'load *.xml' is used in the *.r file.
###################################################################################

logger = logging.getLogger("nomad.QboxXMLParser")

class QboxXMLParserContext(object):

    def __init__(self, writeMetaData = True):
        """Args:
            writeMetaData: Deteremines if metadata is written or stored in class attributes.
        """
        self.writeMetaData = writeMetaData

    def startedParsing(self, fInName, parser):
        """Function is called when the parsing starts.

        Args:
            fInName: The file name on which the current parser is running.
            parser: The compiled parser. Is an object of the class SimpleParser in nomadcore.simple_parser.py.
        """
        self.parser = parser
        self.fName = fInName


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





################################################################################
######################  MAIN PARSER STARTS HERE  ###############################
################################################################################

def build_QboxXMLFileSimpleMatcher():
    """Builds the SimpleMatcher to parse the *.xml file of qbox.

    SimpleMatchers are called with 'SM (' as this string has length 4,
    which allows nice formating of nested SimpleMatchers in python.

    Returns:
       SimpleMatcher that parses *.xml file of qbox.
    """

    geometrySubMatcher = SM(name = 'Geometry',
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
            ], repeats = True)
        ])


    return SM (name = 'Root1',
        startReStr = "",
        sections = ['section_run'],
        forwardMatch = True,
        weak = True,
        subMatchers = [

        SM (name = 'Root2',
            startReStr = "",
            sections = ['section_single_configuration_calculation'],
            forwardMatch = True,
            weak = True,
            subMatchers = [

            geometrySubMatcher


    ]), # CLOSING section_single_configuration_calculation
])


def get_cachingLevelForMetaName(metaInfoEnv, CachingLvl):
    """Sets the caching level for the metadata.

    Args:
        metaInfoEnv: metadata which is an object of the class InfoKindEnv in nomadcore.local_meta_info.py.
        CachingLvl: Sets the CachingLevel for the sections k_band, run, and single_configuration_calculation.
            This allows to run the parser without opening new sections.

    Returns:
        Dictionary with metaname as key and caching level as value.
    """
    # manually adjust caching of metadata
    cachingLevelForMetaName = {
                               'section_run': CachingLvl,
                               'section_single_configuration_calculation': CachingLvl,
                              }
    # Set all band metadata to Cache as they need post-processsing.
    for name in metaInfoEnv.infoKinds:
        if name.startswith('x_qbox_'):
            cachingLevelForMetaName[name] = CachingLevel.Cache
    return cachingLevelForMetaName


def main(CachingLvl):
    """Main function.

    Set up everything for the parsing of the qbox *.xml file and run the parsing.

    Args:
        CachingLvl: Sets the CachingLevel for the sections  run, and single_configuration_calculation.
            This allows to run the parser without opening new sections.
    """
    # get band.out file description
    QboxXMLFileSimpleMatcher = build_QboxXMLFileSimpleMatcher()
    # loading metadata from nomad-meta-info/meta_info/nomad_meta_info/castep.nomadmetainfo.json
    metaInfoPath = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),"../../../../nomad-meta-info/meta_info/nomad_meta_info/qbox.nomadmetainfo.json"))
    metaInfoEnv = get_metaInfo(metaInfoPath)
    # set parser info
    parserInfo = {'name':'qbox-xml-parser', 'version': '1.0'}
    # get caching level for metadata
    cachingLevelForMetaName = get_cachingLevelForMetaName(metaInfoEnv, CachingLvl)
    # start parsing
    mainFunction(mainFileDescription = QboxXMLFileSimpleMatcher,
                 metaInfoEnv = metaInfoEnv,
                 parserInfo = parserInfo,
                 cachingLevelForMetaName = cachingLevelForMetaName,
                 superContext = QboxXMLParserContext())

if __name__ == "__main__":
    main(CachingLevel.Forward)


