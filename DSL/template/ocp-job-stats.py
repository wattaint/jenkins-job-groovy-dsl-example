import httplib
import os
import re
import urllib2

INFLUXDB_HOST           = 'dp-influx.ascendanalyticshub.com'
INFLUXDB_DATABASE       = 'jenkins_ocp_stats'
INFLUXDB_MEASUREMENT    = 'stats'

# Python 2 ----
def merge_two_dict(x, y):
    z = x.copy()
    z.update(y)
    return z
# -------------

def rename_bitbucket_repo(name):
    ret = re.sub(r"^(.*)\/ascendcorp", "https://bitbucket.org/ascendcorp", name)
    ret = re.sub(r".git$", "", ret)
    print(ret)
    return ret

def ocp_name_space_split():
    name = ocp_name_space()
    return name.split("-")

def ocp_name_space():
    ret = "k-k-k"
    kube_file = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
    if os.path.isfile(kube_file):
        with open(kube_file, "r") as f:
            ret = str(f.read()).strip()
    else:
        ret = "e-e-e"
        if "OCP_NAMESPACE" in os.environ.keys():
            ret = os.environ["OCP_NAMESPACE"].split("-")

    return ret

def read_file(filepath):
    exists = os.path.isfile(filepath)
    if exists:
        text = "-"
        with open(filepath, "r") as f:
            text = str(f.read()).strip()
        return text
    else:
        return "file not found."

def get_dsl_git_commit():
    filepath = os.path.join(os.environ["JENKINS_HOME"], "dsl_git_commit")
    return read_file(filepath)

def get_etl_image_version():
    debug = ""
    if os.environ.has_key("ETL_IMAGE_VERSION_FILE_PATH"):
        debug = "env"
        version_file = os.environ["ETL_IMAGE_VERSION_FILE_PATH"]
    else:
        debug = "default"
        version_file = os.path.join(os.environ["JENKINS_HOME"], "image_version/etl_version")
    print("Load version file from {} ({})".format(version_file, debug))
    return read_file(version_file)
    
def get_env_value():
    results = {}
    for key in os.environ.keys():
        if str(key).startswith('DP__'):
            k = str(key.split('DP__')[1]).lower()
            env_value = os.environ[key]
            results[k] = env_value 
            
    return results

def get_fields():
    ret = os.environ

    ret["etl_image_version"]         = get_etl_image_version()
    ret["dsl_git_commit"]            = get_dsl_git_commit()
    ret["dsl_repo"]                  = rename_bitbucket_repo(os.environ.get("BITBUCKET_DSL_REPO", ""))
    ret["dp_build"]                  = os.environ.get("DP__JENKINS_BASE__BUILD_VERSION", "")
    ret["jenkins_version"]           = os.environ.get("JENKINS_VERSION", "")
    ret["ocp_site"]                  = os.environ.get("OCP_SITE_NAME", "")
    ret["env_tier"]                  = os.environ.get("ENV_TIER", "")
    ret["ocp_namespace"]             = ocp_name_space()
    ret["ocp_jenkins_image_version"] = os.environ.get("OPENSHIFT_JENKINS_IMAGE_VERSION", "")
    ret["ocp_build_namespace"]       = os.environ.get("OPENSHIFT_BUILD_NAMESPACE", "")

    ret = merge_two_dict(ret, get_env_value())

    return ret

def get_tags():
    ret = {}
    (app_scope, app_service_group, env_name) = ocp_name_space_split()

    ret["app_scope"]            = app_scope
    ret['app_service_group']    = app_service_group
    ret['env_name']             = env_name
    ret['env_tier']             = os.environ.get("ENV_TIER", "")
    ret["ocp_site"]             = os.environ.get("OCP_SITE_NAME", "")
    ret["ocp_namespace"]        = ocp_name_space()

    return ret

def dict_to_influxdb_format(dict):
    ret = []
    for k, v in dict.iteritems():
        ret.append("{}=\"{}\"".format(k, v))
    return ",".join(ret)

def send_to_influxdb(m, tags, fields):
    atags = dict_to_influxdb_format(tags)
    afields = dict_to_influxdb_format(fields)

    print('')
    print("=== TAG ===")
    print("{}".format("\n".join(atags.split(','))))
    print('')
    print("=== FIELDS ===")
    print("{}".format("\n".join(afields.split(','))))

    data = "{},{} {}".format(INFLUXDB_MEASUREMENT, atags, afields)

    h = httplib.HTTPSConnection(INFLUXDB_HOST)
    h.request('POST', "/write?db={}".format(INFLUXDB_DATABASE), data, {})
    r = h.getresponse()
    print(r.read())

if __name__ == '__main__':
    fields = get_fields()
    tags   = get_tags()

    send_to_influxdb(INFLUXDB_MEASUREMENT, tags, fields)
    