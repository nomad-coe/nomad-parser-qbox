#
# Copyright The NOMAD Authors.
#
# This file is part of NOMAD. See https://nomad-lab.eu for further info.
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

import pytest

from nomad.datamodel import EntryArchive
from qboxparser import QboxParser


def approx(value, abs=0, rel=1e-6):
    return pytest.approx(value, abs=abs, rel=rel)


@pytest.fixture(scope='module')
def parser():
    return QboxParser()


def test_scf(parser):
    archive = EntryArchive()
    parser.parse('tests/data/01_h2ogs.r', archive, None)

    sec_run = archive.run[0]
    assert sec_run.program.version == '1.63.2'

    sec_system = sec_run.system[0]
    assert sec_system.atoms.labels == ['O', 'H', 'H']
    assert sec_system.atoms.positions[1][1].magnitude == approx(7.64131893e-11)
    assert sec_system.atoms.lattice_vectors[2][2].magnitude == approx(8.46683537e-10)

    sec_scc = sec_run.calculation[0]
    assert sec_scc.energy.total.value.magnitude == approx(-7.44295025e-17)
    assert sec_scc.energy.xc.value.magnitude == approx(-1.78424213e-17)


def test_relax(parser):
    archive = EntryArchive()
    parser.parse('tests/data/02_h2ocg.r', archive, None)

    sec_run = archive.run[0]

    sec_systems = sec_run.system
    assert len(sec_systems) == 20
    assert sec_systems[4].atoms.positions[0][2].magnitude == approx(-5.87915881e-16)
    assert sec_systems[7].atoms.lattice_vectors[1][1].magnitude == approx(8.46683537e-10)

    sec_sccs = sec_run.calculation
    assert len(sec_sccs) == 20
    assert sec_sccs[11].energy.total.value.magnitude == approx(-7.44306125e-17)
    assert sec_sccs[18].energy.kinetic_electronic.value.magnitude == approx(5.37898452e-17)
