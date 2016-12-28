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
#
# Before running this script, make sure you set <app.id> in pom.xml.
#
# This script scrapes appengine-web.xml and exports the environemnt
# variables, then runs the App Engine devserver.
#
# Usage:
# $ python run_devserver.py

import os
import signal
import subprocess
import sys

try:
    import yaml
except ImportError:
    print ('PyYaml missing; try installing via:\n'
           '  1. apt-get install libyaml-dev (ubuntu) OR \n'
           '     brew install libyaml (mac)\n'
           '  2. mkvirtualenv policyscanner-dev\n'
           '  3. pip install pyyaml\n')
    sys.exit(1)

from distutils.spawn import find_executable


def run():
    ensure_mvn_installed()
    run_with_env_vars()


def ensure_mvn_installed():
    mvn_cmd = find_executable('mvn')
    if not mvn_cmd:
        print ('I could not find the `mvn` tool. Did you install it?\n'
               'You can download it from here: '
               'http://maven.apache.org/download.cgi')
        sys.exit(1)


def run_with_env_vars():
    """Run the local devserver using the app.yaml environment variables.

    Parse the appengine-web.xml and export the environment variables.
    Then run the devserver.
    """
    with open('./src/main/appengine/app.yaml', 'r') as yaml_file:
        app_yaml = yaml.load(yaml_file)
        env_vars = app_yaml['env_variables']
        for env_var in env_vars:
            var_val = env_vars[env_var]
            print 'Export {} => {}'.format(env_var, var_val)
            os.environ[env_var] = var_val

    subprocess.call(['mvn', 'jetty:run'])


def handle_sigint(signal, frame):
    print 'Exiting'
    sys.exit(0)


if __name__ == '__main__':
    signal.signal(signal.SIGINT, handle_sigint)
    run()
