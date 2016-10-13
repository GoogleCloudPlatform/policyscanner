#!/bin/bash

# Source this script to export the appengine-web.xml env variables
# as your local machine's env variables. This is used for running
# the local devserver.
#
# Be sure to set appengine-web.xml POLICY_SCANNER_EXECUTE_ON_CLOUD
# to FALSE.
#
# Usage:
#
# . ./export_devserver_env.sh

if ! command -v xmlstarlet >/dev/null; then
  echo "This script depends on xmlstarlet, but I don't see it."
  echo "Maybe you should either install it or include its location" \
    "in your PATH environment variable."
  return 1
fi

while read -r line; do
  env_var=$(echo ${line} | cut -f 1 -d '=')
  env_val=$(echo ${line} | cut -f 2 -d '=')
  printf -v $env_var "$env_val"
  export $env_var
done <<< "$(xmlstarlet sel -t -m "//_:appengine-web-app/_:env-variables/_:env-var/@*[name()='name' or name()='value']" -v . -n <src/main/webapp/WEB-INF/appengine-web.xml | sed 'N;s/\n/=/')"

env | grep POLICY
echo done
