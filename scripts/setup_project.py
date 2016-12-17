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

"""Set up the gcloud environment and create a new project with App Engine.

This has been tested with python 2.7.
"""

from environment.gcloud_env import GcloudEnvironment

def run():
    """Run the steps for the gcloud setup."""
    gcloud_config = GcloudEnvironment()
    gcloud_config.ensure_installed()
    gcloud_config.auth_login()
    gcloud_config.create_or_use_project()
    gcloud_config.create_or_use_app()
    gcloud_config.check_billing()
    gcloud_config.enable_apis()
    print 'Done!'

if __name__ == '__main__':
    run()
