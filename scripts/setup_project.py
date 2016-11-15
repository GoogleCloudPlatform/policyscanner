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

# Set up the gcloud environment and create a new project with App Engine.
#
# This has been tested with python 2.7.

from distutils.spawn import find_executable
from subprocess import PIPE, Popen, call
import re
import time

PROJECT_ID_REGEX = re.compile('^[a-z][a-z0-9-]{6,30}$')

class GcloudConfig(object):
    """
    Encapsulate the gcloud setup in the GcloudConfig class
    """

    def __init__(self):
        self.config_name = None
        self.auth_account = None
        self.project_id = None
        self.region = None

    def ensure_installed(self):
        """
        Check whether gcloud tool is installed.
        """
        gcloud_cmd = find_executable('gcloud')
        if gcloud_cmd:
            print 'Found gcloud tool!'
        else:
            raise EnvironmentError(
                'Could not find gcloud. '
                'Have you installed the Google Cloud SDK?\n'
                'You can get it here: https://cloud.google.com/sdk/')

    def auth_login(self):
        """
        User needs to authenticate with Google Cloud Platform account
        before doing anything else.
        """
        return_val = call(['gcloud', 'auth', 'login', '--force'])
        p = Popen(['gcloud', 'auth', 'list',
                   '--filter=status:ACTIVE', '--format=value(account)'],
                  stdout=PIPE, stderr=PIPE)
        stdout, stderr = p.communicate()
        try:
            self.account = stdout.strip()
        except:
            print 'Invalid account, something went wrong'
            raise

    def create_or_use_project(self):
        """
        Create a project or enter the id of a project to use.
        """
        project_id = None

        while True:
            project_choice = raw_input('Which project do you want to use?\n'
                                       '[1] Enter a project id\n'
                                       '[2] Create a new project\n').strip()
            if project_choice == '1':
                project_id = self._use_project()
                break
            elif project_choice == '2':
                project_id = self._create_project()
                break
        self._set_config(project_id)

    def _create_project(self):
        """
        Create the project based on user's input.
        """
        while True:
            project_id = raw_input(
                'Enter a project id '
                '(alphanumeric and hyphens): ').strip()
            if PROJECT_ID_REGEX.match(project_id):
                return_val = call(['gcloud', 'alpha', 'projects', 'create',
                                  project_id])
                if not return_val:
                    return project_id
                    break
        return None

    def _use_project(self):
        """
        Attempt to use a project that the user specifies.
        """
        while True:
            project_id = raw_input('Enter a project id: ').strip()
            return_val = call(['gcloud', 'projects', 'describe',
                               ('--format=table[box,title="Project"]'
                                '(name,projectId,projectNumber)'),
                              project_id])
            if not return_val:
                return project_id
                break
        return None

    def _set_config(self, project_id):
        """
        Save the gcloud configuration for future use, to remember the
        environment for future deployment.
        """
        print 'Trying to activate configuration {}...'.format(project_id)
        return_val = call(['gcloud', 'config', 'configurations', 'activate',
                           project_id])
        if return_val:
            print 'Creating a new configuration for {}...'.format(project_id)
            call(['gcloud', 'config', 'configurations', 'create', project_id])
            call(['gcloud', 'config', 'set', 'account', self.account])

        call(['gcloud', 'config', 'set', 'project', project_id])
        self.project_id = project_id

    def create_or_use_app(self):
        """
        Create App Engine environment for project.
        """
        regions = []
        p = Popen(['gcloud', 'app', 'regions', 'list',
                   '--format=value(region)',
                   '--filter=flexible:True'],
                   stdout=PIPE, stderr=PIPE)
        stdout, stderr = p.communicate()
        try:
            for region in stdout.split('\n'):
                if len(region):
                    regions.append(region.strip())
        except:
            print 'Unable to create App Engine app'

        while True:
            print 'Choose a region for your app to run in:'
            for i, region in enumerate(regions):
                print '[{}] {}'.format(i+1, region)
            try:
                region_index = int(raw_input('Enter a numeric choice: '))
                if region_index > 0 and region_index < len(regions):
                    self.region = regions[region_index]
                    break
            except:
                print 'Invalid entry, try again.'

        call(['gcloud', 'app', 'create', '--region={}'.format(self.region)])

    def check_billing(self):
        """
        Check whether billing is enabled
        """
        print_instructions = True
        while True:
            billing_proc = Popen(['gcloud', 'alpha', 'billing',
                                  'accounts', 'projects', 'describe',
                                  self.project_id],
                                 stdout=PIPE)
            billing_enabled = Popen(['grep', 'billingEnabled'],
                                    stdin=billing_proc.stdout,
                                    stdout=PIPE, stderr=PIPE)
            billing_proc.stdout.close()
            out, err = billing_enabled.communicate()
            if out:
                break
            else:
                if print_instructions:
                    print ('Before enabling the GCP APIs necessary to run '
                           'Policy Scanner, you must enable Billing:\n\n'
                           '    '
                           'https://console.cloud.google.com/'
                           'billing?project={}\n\n'
                           'After you enable billing, setup will continue.\n'
                               .format(self.project_id))
                    print_instructions = False
                time.sleep(1)

    def enable_apis(self):
        """
        Enable the following APIs:
        1. Dataflow
        2. Storage
        3. Resource Manager
        """
        apis = [{'name': 'Dataflow',
                 'service': 'dataflow.googleapis.com'},
                {'name': 'Cloud Storage',
                 'service': 'storage-component-json.googleapis.com'},
                {'name': 'Cloud Storage JSON',
                 'service': 'storage-api-json.googleapis.com'},
                {'name': 'Cloud Resource Manager',
                 'service': 'cloudresourcemanager.googleapis.com'}]

        for api in apis:
            print 'Enabling the {} API...'.format(api['name'])
            call(['gcloud', 'alpha', 'service-management', 'enable',
                  api['service']])

def run():
    """
    Run the steps for the gcloud setup
    """
    gcloud_config = GcloudConfig()
    gcloud_config.project_id = 'hello-world-23'
    gcloud_config.ensure_installed()
    gcloud_config.auth_login()
    gcloud_config.create_or_use_project()
    gcloud_config.create_or_use_app()
    gcloud_config.check_billing()
    gcloud_config.enable_apis()
    print 'Done!'

if __name__ == '__main__':
    run()
