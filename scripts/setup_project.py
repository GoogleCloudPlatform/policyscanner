# This has been tested with python 2.7

from distutils.spawn import find_executable

def ensure_gcloud_installed():
    gcloud_cmd = find_executable('gcloud')
    if gcloud_cmd:
        print 'Found gcloud tool!'
    else:
        print 'Could not find gcloud. Have you installed the Google Cloud SDK?'
        print 'You can get it here: https://cloud.google.com/sdk/'
        exit

def create_or_use_project():
    pass

def create_or_use_app():
    pass

def link_billing():
    pass

def run():
    ensure_gcloud_installed()
    create_or_use_project()
    create_or_use_app()
    link_billing()

    print 'Done!'

if __name__ == '__main__':
    run()
