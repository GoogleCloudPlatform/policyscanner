
"""Wrapper class for Resource Manager API."""

import sys

try:
  from apiclient import discovery
  from apiclient import errors
except:
  print ('To use this, please import the google-api-python-client library:\n\n'
         '$ pip install google-api-python-client\n')
  sys.exit(1)

from oauth2client.client import GoogleCredentials

class ResourceManager(object):
    """Resource Manager wrapper around service discovery API."""

    def __init__(self, credentials=None):
        if not credentials:
            credentials = GoogleCredentials.get_application_default()
        self.credentials = credentials
        self._service = discovery.build(serviceName='cloudresourcemanager',
                                       version='v1',
                                       credentials=self.credentials)

    @property
    def service(self):
        """Returns the Resource Manager service."""
        return self._service
