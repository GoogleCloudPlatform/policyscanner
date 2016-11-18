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

def run():
    ensure_mvn_installed()
    project_id = find_project_id()
    print 'Project Id: {}'.format(project_id)
    run_with_env_vars(project_id)

def ensure_mvn_installed():
    mvn_cmd = find_executable('mvn')
    if not mvn_cmd:
        print ('I could not find the `mvn` tool. Did you install it?\n'
               'You can download it from here: '
               'http://maven.apache.org/download.cgi')
        sys.exit(1)

def find_project_id():
    """
    Parse pom.xml to get the project_id and replace 
    """
    tree = ETree.parse('./pom.xml')
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

    tree = ETree.parse('./src/main/webapp/WEB-INF/appengine-web.xml')
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
