# Copyright 2016 Google Inc.
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

# Create GCS bucket that stores known-good policies as well as create
# baseline policy files for each project specified in an input file.
#
# This has been tested with python 2.7.

import argparse

from distutils.spawn import find_executable
from subprocess import PIPE, Popen, call

def create_or_use_bucket():
    """
    Create or use a specified GCS bucket to store the policy files. Also
    turn on object versioning on the bucket.
    """
    pass

def setup_project_folders():
    """
    Read in an input file (optional) of specific project ids or read in all
    projects in organization and create folders (with the project id as the
    name) for each one.
    """
    pass

def create_base_policies():
    """
    For each project, get its IAM policy and save as a json file in its
    respective GCS bucket.
    """
    pass

def run():
    create_or_use_bucket()
    setup_project_folders()
    create_base_policies()

if __name__ == '__main__':
    run()
