#!/bin/bash

git clone --recursive git@gitlab.mpcdf.mpg.de:nomad-lab/nomad-lab-base.git
git submodule foreach git checkout master
pip install -r nomad-lab-base/python-common/requirements.txt
