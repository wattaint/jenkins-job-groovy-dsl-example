function log {
  echo "[$(date "+%Y-%m-%d %H:%M:%S")] [Vault] $1"
}

function check_error_response {
  json_resp=$1
  log "-- Checking error response --"
  errors=$(echo $json_resp | jq -r '.errors')
  if [ "${errors}" != "null"  ]; then
    log "Response Error!! ${errors}"
    log "message: ---"
    jq . <<< $json_resp
    log "Exit .."
    exit 1
  fi
}

APP_NAME=acn-dp-generic-database-etl
VALUR_URL="https://vault-cluster.common-cicd-platform.svc:8200"

log "---------------------------"
log "Application Name: ${APP_NAME}"
log "Vault Url: ${VALUR_URL}"

TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
OCP_NAMESPACE=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace)
IFS=- read APP_SCOPE APP_SERVICE_GROUP  ENV_NAME <<< $OCP_NAMESPACE

resp=$(curl -s -X GET -k ${VALUR_URL}/v1/sys/leader)
check_error_response $resp
# echo "==== json response ===="
# jq . <<< $resp

export VAULT_LEADER=$(echo $resp | jq -r '.leader_cluster_address')
log "Vault Leader: ${VAULT_LEADER}"

vault_json_data=$(  jq -n \
  --arg jwt "${TOKEN}" \
  --arg role "${APP_SCOPE}-${APP_SERVICE_GROUP}-read-only-${ENV_NAME}-role" \
  '{jwt: $jwt, role: $role}' )

resp=$(curl -s \
  --request POST \
  --data \
  "${vault_json_data}" -k ${VAULT_LEADER}/v1/auth/kubernetes/login )

check_error_response $resp
#echo "==== /v1/auth/kubernetes/login == json response ===="
#jq . <<< $resp

export VAULT_TOKEN=$(echo ${resp} | jq -r '.auth.client_token' )
log "Value Token: ${VAULT_TOKEN}"

resp=$(curl --silent -H "X-Vault-Token: $VAULT_TOKEN" -X GET -k ${VAULT_LEADER}/v1/secret/$APP_SCOPE/$APP_SERVICE_GROUP/$ENV_NAME/apps/$APP_NAME)
check_error_response $resp

VAULT_DATA=$(echo $resp | jq -r '.data')
# echo "---- vault data ----"
# echo $VAULT_DATA

IS_QUERY_SUCCESS=$(echo ${VAULT_DATA} | jq -r '.isQuerySuccess')
if [[ ${IS_QUERY_SUCCESS} != "true" ]]; then
  if [ "${IS_QUERY_SUCCESS}" == "null" ]; then
    log "field IS_QUERY_SUCCESS not found skip checking .."
  else
    log "Default Value of isQuerySuccess is ${IS_QUERY_SUCCESS}."
    log "ERROR!! : Cannot fetch data from vault."
    exit 1
  fi
elif [[ ${IS_QUERY_SUCCESS} == "true" ]]; then
  log "Default Value of isQuerySuccess is ${IS_QUERY_SUCCESS}."
  log "SUCCESS!! : Fetched data from vault."
fi
