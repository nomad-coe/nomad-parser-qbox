#
# Copyright The NOMAD Authors.
#
# This file is part of NOMAD.
# See https://nomad-lab.eu for further info.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import re
import numpy as np

from nomad.parsing.file_parser import BasicParser
from nomad.units import ureg


class QboxParser(BasicParser):
    def __init__(self):
        re_f = r'\-*\d+\.\d+E*e*\-*\+*\d*'

        def get_positions(val):
            labels = re.findall(r'atom name\=\"([A-Z][a-z]*)\w*\"', val)
            positions = re.findall(rf'\<position\> *({re_f} +{re_f} +{re_f})', val)
            forces = re.findall(rf'\<force\> *({re_f} +{re_f} +{re_f})', val)
            try:
                positions = np.array([p.split() for p in positions], dtype=np.dtype(np.float64))
                forces = np.array([f.split() for f in forces], dtype=np.dtype(np.float64))
            except Exception:
                pass
            return dict(atom_labels=labels, atom_positions=positions, atom_forces=forces)

        super().__init__(
            specifications=dict(
                name='parsers/qbox', code_name='qbox', code_homepage='http://qboxcode.org/',
                domain='dft', mainfile_mime_re=r'(application/xml)|(text/.*)',
                mainfile_contents_re=(r'http://qboxcode.org')),
            units_mapping=dict(length=ureg.bohr, energy=ureg.hartree),
            program_version=r'I qbox ([\d\.]+)',
            lattice_vectors=rf'\<unit\_cell\s+(a\=[\s\S]+?)\/\>',
            atom_labels_atom_positions_atom_forces=(
                r'(\<atom name\=[\s\S]+?)\<\/atomset\>', get_positions),
            electronic_kinetic_energy=rf'\<ekin\> *({re_f}) *\<\/ekin\>',
            energy_total=rf'\<etotal\> *({re_f}) *\<\/etotal\>',
            energy_XC=rf'\<exc\> *({re_f}) *\<\/exc\>')
