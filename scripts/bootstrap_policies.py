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
# the project folders that will hold the known-good policy files.
#
# This has been tested with python 2.7.

import argparse

from environment.gsutil_env import GsutilEnvironment, ScannerBucketObject

def run():
    ap = argparse.ArgumentParser()
    ap.add_argument('-p', '--project_file', required=False,
                    help='An optional input file of project ids')
    args = ap.parse_args()

    policy_bkt_setup = GsutilEnvironment(projects_filename=args.project_file)
    policy_bkt_setup.ensure_environment()
    policy_bkt_setup.choose_bucket(ScannerBucketObject.POLICY)
    policy_bkt_setup.choose_bucket(ScannerBucketObject.OUTPUT)
    policy_bkt_setup.setup_project_folders()
    print policy_bkt_setup

if __name__ == '__main__':
    run()
