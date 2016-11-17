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

from environment.gsutil_env import GsutilEnvironment, ScannerBucketObject

def run():
    policy_bkt_setup = GsutilEnvironment()
    policy_bkt_setup.ensure_environment()
    policy_bkt_setup.choose_bucket(ScannerBucketObject.POLICY)
    policy_bkt_setup.choose_bucket(ScannerBucketObject.OUTPUT)
    policy_bkt_setup.setup_project_policies()
    print policy_bkt_setup

if __name__ == '__main__':
    run()
