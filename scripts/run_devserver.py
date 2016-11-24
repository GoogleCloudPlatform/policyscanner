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
import re
import signal
import subprocess
import sys
import xml.etree.ElementTree as ETree

from distutils.spawn import find_executable

APPID_REGEX = re.compile('\$\{app\.id\}')
APPENGINEWEB_LOCATION = './src/main/webapp/WEB-INF/appengine-web.xml'
POMXML_LOCATION = './pom.xml'

def run():
    ensure_cwd_project_root()
    ensure_mvn_installed()
    project_id = find_project_id()
    print 'Project Id: {}'.format(project_id)
    run_with_env_vars(project_id)

def ensure_cwd_project_root():
    """
    Ensure that the current working directory is the root of the project
    directory, i.e. the scripts/ directory should be in the cwd.
    """
    if not os.path.isfile('./scripts/run_devserver.py'):
        print ('Run this script from the root Policy Scanner directory, i.e.'
               '\n\n    python ./scripts/run_devserver.py\n')
        sys.exit(1)

def ensure_mvn_installed():
    """
    Check to make sure that Maven (mvn) is installed.
    """
    mvn_cmd = find_executable('mvn')
    if not mvn_cmd:
        print ('I could not find the `mvn` tool. Did you install it?\n'
               'You can download it from here: '
               'http://maven.apache.org/download.cgi')
        sys.exit(1)

def find_project_id():
    """
    Parse pom.xml to get the project_id
    """
    tree = ETree.parse(POMXML_LOCATION)
    root = tree.getroot()
    project_id = None

    ns = {'maven': 'http://maven.apache.org/POM/4.0.0'}
    for properties in root.findall('maven:properties', ns):
        for prop in properties.findall('maven:app.id', ns):
            project_id = prop.text

    if project_id == 'YOUR_PROJECT_ID':
        project_id = None
    return project_id

def run_with_env_vars(project_id):
    """
    Parse the appengine-web.xml and export the environment variables.
    Then run the devserver.
    """
    if not project_id:
        print 'Invalid project id! Did you set it in the <app.id> in pom.xml?'
        sys.exit(1)

    tree = ETree.parse(APPENGINEWEB_LOCATION)
    root = tree.getroot()
    ns = {'appengine': 'http://appengine.google.com/ns/1.0'}
    env_vars = root.findall('appengine:env-variables', ns)
    for vars in env_vars:
        for env_var in vars.findall('appengine:env-var', ns):
            nv = env_var.attrib
            var_nm = nv['name']
            var_val = nv['value']
            if APPID_REGEX.findall(var_val):
                var_val = APPID_REGEX.sub(project_id, var_val)
            print 'Export {} => {}'.format(var_nm, var_val)
            os.environ[var_nm] = var_val

    subprocess.call(['mvn', 'appengine:devserver'])

def handle_sigint(signal, frame):
    print 'Exiting'
    sys.exit(0)

if __name__ == '__main__':
    signal.signal(signal.SIGINT, handle_sigint)
    run()
