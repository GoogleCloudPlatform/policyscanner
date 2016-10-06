# Policy Scanner

Policy Scanner scans your projects' policies and alerts you if they
deviate from a known-good.
This means you can link a source-controlled repository full of your
projects' desired policies to the scanner, and get alerts every time a
project's policy doesn't match its intended value.
This allows an organization to set up a system of review for making
changes to policies and such.

# Installation

You will need to deploy this app to App Engine to use it. Also, you will
need to create an organization.

1. Set-up Google Cloud to deploy the scanner.
  1. Create a new project for the scanner by going to the
     Google Cloud Console and clicking on "Create Project".
     Note the project ID of this project.
  2. Enable Billing for this project.
  3. Enable the following APIs:
     * Cloud Resource Manager
     * Cloud Dataflow
     * Cloud Storage
  4. Add the "Compute Engine default service account"
     (<YOUR_PROJECT_ID>-compute@developer.gserviceaccount.com) to the
     Organization-level IAM settings and give it the "Browser" role.

2. Check your policies into Google Cloud Storage (GCS).
  1. Create a bucket in GCS (or use an existing one).
     This is where your known-good policies will reside.
  2. Create a folder with the same name as your organization in
     the bucket that you just created.
  3. For every project in the organization whose policy you wish to
     check in, create a folder with the same name as the project.
  4. Inside every project folder that you created, create a file named
     "POLICY" (has to be all uppercase) which contains a list of
     bindings that define that policy in JSON form.
     Make sure that the type of this file is set to
     "text/plain" in GCS. A sample policy file has been
     provided in resources/examples/policies.
     The standard url for a project's policy would be
     `gs://bucket-name/org-id/project-id/POLICY`.

3. Customize the scanner for your org before deploying.
  1. Clone the git repository by running
     `git clone https://github.com/google/policyscanner`
  1. Enter the project ID of the project you created in the `<app.id>`
     field in the pom.xml file in the root of the git directory.
  2. Enter a version for your app in the `<app.version>` field in the
     pom.xml file. This is set to 1 by default.
  3. If you are running one of the built-in pipelines make sure you update all
     the environment variables needed by it in
     `src/main/webapp/WEB-INF/appengine-web.xml`. All variables need to be set.
    1. `POLICY_SCANNER_ORG_NAME` is the name of the org you want to scan.
    2. `POLICY_SCANNER_ORG_ID` is the numeric ID of the org you want to scan.
    3. `POLICY_SCANNER_INPUT_REPOSITORY_URL` is the name of the GCS bucket that
       contains the checked-in policies.
    4. `POLICY_SCANNER_SINK_URL` is the url of the file to which the output of
       the scanner will be written, e.g. `gs://bucket-name/OUTPUT`.
    5. `POLICY_SCANNER_EXECUTE_ON_CLOUD` is a boolean which is set to "TRUE" if
       the pipeline is supposed to execute on Google Cloud and "FALSE" if it is
       supposed to run using the local Dataflow runner.
    6. `POLICY_SCANNER_DATAFLOW_TMP_BUCKET` is a GCS bucket that Dataflow can
       use to store some temporary files during the execution of the pipeline.
       Only used when running the pipeline on Google Cloud.

4. Deploy the app.
  1. If haven't already, install the [Google Cloud SDK](https://cloud.google.com/sdk/downloads).
  2. Authenticate with Google Cloud by running `gcloud auth login`.
     Make sure you login with the same account that you created
     the project under.
  3. Once you are logged in, you can deploy the app to App Engine by
     navigating to the root of the git directory and running
     `mvn appengine:update`.
  4. Once the app has deployed, you can point your browser to
     [https://<YOUR_PROJECT_ID>.appspot.com/check\_states]()
     to see the result of executing the scanner on your organization's
     projects.

# Sync a git repo

If you store all of your known-good policies in a git repo, you can
sync it to a GCS bucket and use the above workflow to run the scanner.
To set-up the git sync:

1. Open `src/main/webapp/WEB-INF/appengine-web.xml`.
2. Change the environment variable `POLICY_SCANNER_GIT_REPOSITORY_URL`
   to the URL of the git repository you wish to sync.
3. Change the environment variable `POLICY_SCANNER_GIT_REPOSITORY_NAME`
   to the name of the git repository you wish to sync.
4. Change the environment variable `POLICY_SCANNER_GIT_SYNC_DEST_BUCKET`
   to the name of the GCS bucket you wish to sync it to.

Then, when you deploy your webapp, you can go to `/git_sync` to launch
the sync. The files will be synced to the GCS bucket you specified.

# Concept

The functional unit of the processing for this tool is an action.
An action is just a Dataflow DoFn transform that accepts some input,
processes it, and produces an output, with some caveats.
An action can accept multiple inputs, and have multiple outputs,
which is realized through side inputs and side outputs.
For more information on this, read
[https://cloud.google.com/dataflow/model/par-do](Dataflow Transforms).


You can chain actions to make a pipeline and execute it in the cloud.
You can find built-in actions in
com.google.cloud.security.scanner.actions.
If you need a action that's not provided, you can write your own and
combine it with the provided actions.

There also exist pre-built pipelines which can be found in
`com.google.cloud.security.scanner.pipelines`. To use the built-in
pipelines, you have to modify the hardcoded bucket and organization IDs
parameters in the servlets that use these pipelines.
The servlets are located in com.google.cloud.security.scanner.testing.
There are two pipelines that are provided:
- `LiveStateChecker`: This compares the policies of all the projects in
  your organization with their checked-in counterparts in GCS and writes
  the output, which consists of all the discrepancies, in a file called
  `checkedStateOutputMessages.txt-00000-of-00001` in the root of the
  bucket specified in the code.
- `DesiredStateEnforcer`: This compares the policies of all the projects
  in your organization with their checked-in counterparts in
  GCS and automatically fixes the policy of the project
  to the one specified in repository in Google Cloud Storage.
  Additionally, a log of all the policies changed is written to a file
  called `checkedStateOutputMessages.txt-00000-of-00001` in the root of
  the bucket specified in the code.

Once you have made the desired changes, make sure that the App Engine
configuration file in `src/main/webapp/WEB-INF/appengine-web.xml` reflects
the project ID for the project you wish to deploy the scanner under.
Additionally, make sure all the URL routing is setup in
`src/main/webapp/WEB-INF/web.xml`. There are some sample routes that you
can use to write your own.
The file `src/main/webapp/WEB-INF/cron.xml` contains the cron job
description which describes how often your job is supposed to run. You
can configure it to hit your desired URL endpoints at regular intervals.

Once you have done that, navigate to the root of the project and run
`mvn appengine:update` to deploy the app.

Make sure that you are logged into Google Cloud on your system by
running `gcloud auth login`. This will open a browser window where you
can login to your Google account.

# Checking Output

The pipeline will write the output of the job to the file specified in the
environment variables in appengine-web.xml. When running the App Engine
development server, the variables need to be set manually in the shell.
