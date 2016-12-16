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

"""A tool to set up the gcloud/gsutil environment.

Initializes the gsutil environment and creates buckets and folders that hold
the known-good policies for projects.

This has been tested with python 2.7.
"""

import json
import os
import re
import sys
import time

from distutils.spawn import find_executable
from gcloud_env import GcloudEnvironment
from resource_mgr import ResourceManager
from subprocess import PIPE, Popen, call

class GsutilEnvironment(object):
    """The environent for automating the bucket setup.

    Encapsulates the environment for gsutil and sets up
    the buckets and base policy files.
    """

    DEFAULT_BUCKETNAME_POLICIES = 'gs://{}_policies'
    DEFAULT_BUCKETNAME_OUTPUT = 'gs://{}_output'
    GCS_LS_ERROR_REGEX = re.compile('^(.*Exception): (\d{3})', re.MULTILINE)

    def __init__(self, projects_filename=None):
        self.gcloud_config = GcloudEnvironment()
        self.policy_bucket = None
        self.output_bucket = None
        self.projects_filename = projects_filename

    def __repr__(self):
        return ('GsutilEnvironment:\n'
                '  {}\n'
                '  Policy bucket: {}\n'
                '  Output bucket: {}\n'
                .format(self.gcloud_config,
                        self.policy_bucket,
                        self.output_bucket))

    def ensure_environment(self):
        """Ensure that the environment is properly set up.

        Check the following:
        1. gsutil tool is installed
        2. gcloud tool/environment is logged in (gcloud auth login)
        3. A project_id has been set on the environment
        """
        gsutil_cmd = find_executable('gsutil')
        if gsutil_cmd:
            print 'Found gsutil tool!'
        else:
            raise EnvironmentError(
                'Could not find gsutil. '
                'Have you installed the Google Cloud SDK?\n'
                'You can get it here: https://cloud.google.com/sdk/')
        p = Popen(['gcloud', 'info', '--format=json'], stdout=PIPE, stderr=PIPE)
        output, err = p.communicate()
        try:
            gcloud_info = json.JSONDecoder().decode(output)
            if 'config' in gcloud_info:
                self.gcloud_config.auth_account = gcloud_info['config']['account']
                self.gcloud_config.project_id = gcloud_info['config']['project']
        except ValueError:
            print 'Failed to get gcloud info, will initialize gcloud'

        if not self.gcloud_config.auth_account:
            self.gcloud_config.auth_login()

        if not self.gcloud_config.project_id:
            self.gcloud_config.create_or_use_project()

        print self.gcloud_config

    def choose_bucket(self, object_type):
        """Choose a bucket to use for the policies.

        Create or use a specified GCS bucket to store the specified object.
        Also turn on object versioning on the bucket if it stores policy
        objects.
        """
        p = Popen(['gsutil', 'ls'], stdout=PIPE, stderr=PIPE)
        output, err = p.communicate()
        if p.returncode:
            print err
            from sys import exit
            exit(p.returncode)

        prompt_tmpl = ('+----------------------+\n'
                        '| Set up {} bucket |\n'
                        '+----------------------+\n'
                        'Specify a bucket for {}:')
        prompt_header = ''
        if object_type == ScannerBucketObject.POLICY:
            prompt_header = prompt_tmpl.format('POLICY', 'your policies')
        elif object_type == ScannerBucketObject.OUTPUT:
            prompt_header = prompt_tmpl.format('OUTPUT', 'the scanner output')

        while True:
            bucket_choice = raw_input(
                ('{}\n'
                 '[1] Enter bucket name\n'
                 '[2] Choose from list\n'
                 'Enter your numeric choice: '
                 .format(prompt_header))).strip()

            bucket = None
            if bucket_choice == '1':
                bucket = self._create_or_reuse_bucket(object_type)
            elif bucket_choice == '2':
                bucket = self._choose_bucket(output)

            if not bucket:
                continue

            if not bucket.endswith('/'):
                bucket = bucket + '/'

            if object_type == ScannerBucketObject.POLICY:
                # if policy bucket, enable versioning
                self.policy_bucket = bucket
                self._enable_object_versioning(bucket)
            elif object_type == ScannerBucketObject.OUTPUT:
                # warn if using same buckets, prefer not to version
                # output and temp files
                if bucket == self.policy_bucket:
                    same_bucket = raw_input(
                        'I don\'t recommend using the same bucket for '
                        'both your known-good policies AND output files!\n'
                        'Continue anyway? y/N ').strip().lower()
                    if same_bucket != 'y':
                        continue
                self.output_bucket = bucket

            print 'Using bucket: {}\n'.format(bucket)
            break

    def _create_or_reuse_bucket(self, object_type):
        """Give the user the option to either create or re-use a bucket.

        This method can be used for either the POLICY or the OUTPUT
        bucket types.
        """
        bucket_name = None

        default_bucket = ''
        if object_type == ScannerBucketObject.POLICY:
            default_bucket = self.DEFAULT_BUCKETNAME_POLICIES
        elif object_type == ScannerBucketObject.OUTPUT:
            default_bucket = self.DEFAULT_BUCKETNAME_OUTPUT

        default_bucket = default_bucket.format(self.gcloud_config.project_id)

        while True:
          bucket_name = raw_input('Enter a bucket name (default: {}): '
                                  .format(default_bucket)).strip()
          if not bucket_name:
              bucket_name = default_bucket

          if not bucket_name.startswith('gs://'):
              bucket_name = 'gs://{}'.format(bucket_name)

          p = Popen(['gsutil', 'mb', bucket_name], stdout=PIPE, stderr=PIPE)
          out, err = p.communicate()
          if p.returncode:
              print err

          if not p.returncode or \
              self._should_force_use_bucket(bucket_name, err):
              break

        return bucket_name

    def _should_force_use_bucket(self, bucket_name, mb_error_text):
        """Determine whether we should use the existing bucket.

        Parse the error text from gsutil mb and check what the
        exception is. If it's a 409, confirm with the user whether
        they actually want to use that bucket name.
        """
        exceptions = self.GCS_LS_ERROR_REGEX.findall(mb_error_text)
        if not len(exceptions):
            return False

        mb_exception, res_code_mb = exceptions[0]
        if mb_exception == 'ServiceException' and int(res_code_mb) == 409:
            p = Popen(['gsutil', 'ls', bucket_name],
                      stdout=PIPE, stderr=PIPE)
            ls_out, ls_err = p.communicate()
            ls_exceptions = self.GCS_LS_ERROR_REGEX.findall(ls_err)
            if p.returncode:
                print ls_err

            if len(ls_exceptions):
                ls_exception, res_code_ls = ls_exceptions[0]
                if int(res_code_ls) == 403:
                    print 'You can\'t use this bucket.'
                    return False

        while True:
          should_use = raw_input(
              'Re-use the bucket "{}"? (y/N) '
              .format(bucket_name)).strip().lower()

          if should_use == 'y':
              return True
          elif should_use == 'n':
              break

        return False

    def _choose_bucket(self, gsls_output):
        """Present a list of the buckets (from gsutil ls)."""
        bucket = None
        buckets = []
        print 'Which bucket do you want to use?'
        for i, line in enumerate(gsls_output.split('\n')):
            line = line.strip()
            if len(line):
                print '[{}] {}'.format(i+1, line)
                buckets.append(line)

        while True:
            entry = raw_input(('Enter your numeric choice '
                               '(press ENTER to return): ')).strip()
            if not entry:
                return None
            try:
                entry_idx = int(entry)
                if entry_idx-1 < 0 or entry_idx > len(buckets):
                    raise IndexError

                bucket = buckets[entry_idx-1]
                break
            except ValueError:
                print 'Expected a number'
            except IndexError:
                print 'Expected a numeric choice from above'
        return bucket

    def _enable_object_versioning(self, bucket):
        """Enable object versioning on the bucket level."""
        p = Popen(['gsutil', 'versioning', 'set', 'on', bucket],
                  stdout=PIPE, stderr=PIPE)
        out, err = p.communicate()
        print err
        if p.returncode:
            raise InvalidBucketException(
                'Abort operation, the bucket is invalid')

    def setup_project_folders(self):
        """Setup the project folders for Policy Scanner.

        Read in an input file (optional) of specific project ids or read in all
        projects in organization and create folders (with the project id as the
        name) for each one.

        For each project, get its IAM policy and save as a json file in its
        respective GCS bucket.
        """
        resource_mgr = ResourceManager()
        if self.projects_filename:
            cwd = os.path.abspath(os.path.dirname(sys.argv[0]))
            policy_dir = cwd+'/policies'
            if not os.path.exists(policy_dir):
                os.mkdir(policy_dir)

            with open(self.projects_filename, 'r') as projects:
                for project in projects:
                    project_id = project.strip()
                    project = (resource_mgr.service.projects()
                               .get(projectId=project_id).execute())
                    policy_path = '{}/{}/{}'.format(
                        policy_dir, project['parent']['id'], project_id)
                    if not os.path.exists(policy_path):
                        os.makedirs(policy_path)
                    iam_policy = (resource_mgr.service
                                  .projects()
                                  .getIamPolicy(resource=project_id, body={})
                                  .execute())
                    with open('{}/POLICY'.format(policy_path),
                              'w') as policy_file:
                        json.dump(iam_policy, policy_file)

                    time.sleep(0.5)
        else:
            # TODO(carise): read all the projects from organization
            pass

        print 'Upload policy files...'
        p = call(['gsutil', '-m', 'cp', '-r', '{}/*'.format(policy_dir),
                   self.policy_bucket])

        time.sleep(0.5)
        p = call(['gsutil', 'setmeta', '-h', 'Content-Type:text/plain',
                  '{}*/*/POLICY'.format(self.policy_bucket)])

class ScannerBucketObject:
    """Types of bucket objects for Policy Scanner."""
    _unused, POLICY, OUTPUT = range(3)


class InvalidBucketException(Exception):
    """Exception class for invalid bucket name."""
    pass
