import urllib
import urllib2
import ssl
import json

APP_NAME            = "acn-dp-generic-database-etl"
NAMESPACE_FILE      = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
TOKEN_FILE          = "/var/run/secrets/kubernetes.io/serviceaccount/token"
VAULT_LEADER_URL    = "https://vault-cluster.common-cicd-platform.svc:8200/v1/sys/leader"

def request(req):
    context = ssl._create_unverified_context()
    response = urllib2.urlopen(
        req,
        context=context
    )

    return json.load(response)

def get_ocp_namespace():
    ret = ""
    with open(NAMESPACE_FILE, "r") as f:
        ret = str(f.read())
    return ret

def get_token():
    token = None
    with open(TOKEN_FILE, "r") as f:
        token = f.read()
    return token

def get_leader_cluster():
    req = urllib2.Request(VAULT_LEADER_URL)
    data = request(req)
    
    return data['leader_cluster_address']

def get_client_token(leader_cluster_address, app_scope, app_service_group, env_name):
    token = get_token()
    url = "{}/v1/auth/kubernetes/login".format(leader_cluster_address)
    params = {
        'jwt': token,
        'role': "{}-{}-read-only-{}-role".format(app_scope, app_service_group, env_name)
    }

    data = json.dumps(params)
    
    req = urllib2.Request(url, data, {'Content-Type': 'application/json'})
    data = request(req)

    vault_token = data['auth']['client_token']
    return vault_token

def get_vault_data(app_name, leader_cluster_address, app_scope, app_service_group, env_name):
    vault_token = get_client_token(leader_cluster_address, app_scope, app_service_group, env_name)
    
    req = urllib2.Request(
        "{}/v1/secret/{}/{}/{}/apps/{}".format(
            leader_cluster_address,
            app_scope,
            app_service_group,
            env_name,
            app_name),
        headers={"X-Vault-Token" : vault_token}
    )
    data = request(req)
    return data

ocp_namespace = get_ocp_namespace()
(app_scope, app_service_group, env_name) = ocp_namespace.split("-")

leader_cluster_address = get_leader_cluster()

width = 20
print '--------------------'
print "Leader cluster = ".rjust(20, ' '),       leader_cluster_address
print "app_scope = ".rjust(width, ' '),         app_scope
print "app_service_group = ".rjust(width, ' '), app_service_group
print "env_name = ".rjust(width, ' '),          env_name

print '--------------------'
print
data = get_vault_data(APP_NAME, leader_cluster_address, app_scope, app_service_group, env_name)
print(json.dumps(data, indent=4, sort_keys=True))
