// import org.yaml.snakeyaml.Yaml

itemList = evaluate("""
    @Grab('org.yaml:snakeyaml')
    import org.yaml.snakeyaml.Yaml
    Yaml parser = new Yaml()
    def itemList = parser.load(("$WORKSPACE/$CHECKOUT_PATH/$ENV_TIER/jobs.yaml" as File).text)
    println "========== jobs.yaml ============"
    Yaml yaml = new Yaml()
    output = yaml.dump(itemList)
    println output
    println "=================================="
    return itemList
""")

String serviceAccountJson = itemList.serviceAccountJson
String secretName = itemList.secretName

ArrayList jobList = itemList.jobs
createDirJobs(null, jobList, serviceAccountJson, secretName)

def createDirJobs(String folderName, ArrayList collection, String serviceAccountJson, String secretName) {
    if (folderName != null) {
        folder(folderName) {
           description('Folder contains sub-folders and jobs')
        }
    }

    for(LinkedHashMap item in collection) {
        item.each { key, value ->
            if (value instanceof LinkedHashMap) {
                createJob(folderName, key, value, serviceAccountJson, secretName)
            }
            else {
                subFolderName = getPath(folderName, key)
                createDirJobs(subFolderName, value, serviceAccountJson, secretName)
            }
        }
    }
}

def getPath(folderName, subName) {
    if (folderName == null) {
        return subName
    }
    else if (subName == null) {
        return folderName
    }
    else {
        return folderName + '/' + subName
    }
}

def createJob(String folderName, String jobName, LinkedHashMap params, String serviceAccountJson, String secretName) {
    job(getPath(folderName, jobName)) {
        String paramsString = ""
        parameters {
            params.each { key, value ->
                if (key != "trigger_cron") {
                    
                    if (value.contains(' '))
                            value = '\"'+value+'\"'
                            
                    stringParam(key, value, '')
                    
                    if (key != "podType"){                        
                        paramsString += getParamString(key, value)                    
                    }
                }
            }
        }
        
        logRotator {
            numToKeep(14)
        }
        
        if (params.trigger_cron != null){
            triggers {
                cron(params.trigger_cron)
            }
        }
        
        String podTypeString = "etl-template.yaml"
        if (params.podType != null){
            podTypeString = params.podType
        }

        steps {
            shell('''\

### GET VAULT TOKEN
VAULT_LEADER=$(curl -X GET -k https://vault-cluster.common-cicd-platform.svc:8200/v1/sys/leader |jq-linux64 -r '.leader_cluster_address')

OCP_NAMESPACE=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace)
IFS=- read APP_SCOPE APP_SERVICE_GROUP  ENV_NAME <<< $OCP_NAMESPACE

TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)
VAULT_TOKEN=$(curl --request POST --data "{\\"jwt\\": \\"$TOKEN\\", \\"role\\": \\"$APP_SCOPE-$APP_SERVICE_GROUP-read-only-$ENV_NAME-role\\"}" -k ${VAULT_LEADER}/v1/auth/kubernetes/login | jq-linux64 -r .auth.client_token)

APP_NAME=acn-dp-generic-database-etl
VAULT_DATA=$(curl --silent -H "X-Vault-Token: $VAULT_TOKEN" -X GET -k ${VAULT_LEADER}/v1/secret/$APP_SCOPE/$APP_SERVICE_GROUP/$ENV_NAME/apps/$APP_NAME | jq-linux64 -r .data)

IS_QUERY_SUCCESS=$(echo ${VAULT_DATA})
if [[ -z "${IS_QUERY_SUCCESS}" ]]; then
  echo "Default Value of isQuerySuccess is ${IS_QUERY_SUCCESS}."
 echo "ERROR!! : Cannot fetch data from vault."
 exit 1
else
 echo "Default Value of isQuerySuccess is ${IS_QUERY_SUCCESS}."
 echo "SUCCESS!! : Fetched data from vault."
fi


### ETL
export PARAMS="'''+paramsString+'''"
export podTypeTemplate="'''+podTypeString+'''"
# Escape "/" -> "\\/" and "&" -> "\\&" charactor
export ESC_PARAMS=`echo $PARAMS |sed -e 's#&#\\\\\\\\\\\\\\\\\\\\\\\\&#g' |sed -e 's#/#\\\\\\/#g'`

export TAG_NAME=$(cat $JENKINS_HOME/image_version/acm.dp.eq.generic.database.etl.version)
sed "s/%BUILD_NUMBER%/${BUILD_NUMBER}/g" ~/template/${podTypeTemplate} \\
|sed "s/%JOB_BASE_NAME%/${JOB_BASE_NAME}/g" \\
|sed "s/%SERVICE_ACCOUNT%/'''+serviceAccountJson+'''/g" \\
|sed "s/%SECRET_NAME%/'''+secretName+'''/g" \\
|sed "s/%TAG_NAME%/${TAG_NAME}/g" \\
|sed "s#%VAULT_LEADER%#${VAULT_LEADER}#g" \\
|sed "s/%VAULT_TOKEN%/${VAULT_TOKEN}/g" \\
|sed "s/%ENV_TIER%/${ENV_TIER}/g" \\
|sed "s/%PARAMS%/$ESC_PARAMS/g" \\
> etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml

cp ~/template/wait-until-pod.sh wait-until-pod.sh
chmod 755 wait-until-pod.sh

cat etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml
oc create -f etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml

# Wait until the pods is running, within 5 mins timeout
POD_NAME=etl-${JOB_BASE_NAME}-${BUILD_NUMBER}

./wait-until-pod.sh $POD_NAME Pending
./wait-until-pod.sh $POD_NAME Running

# Pipe Log to Jenkins Console
oc logs -f etl-${JOB_BASE_NAME}-${BUILD_NUMBER} || true
''')
            shell('''\
POD_NAME=etl-${JOB_BASE_NAME}-${BUILD_NUMBER}
POD_PHASE=$(oc get po "$POD_NAME" --template={{.status.phase}})
oc describe pod $POD_NAME
oc delete pod $POD_NAME
rm etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml
if [ "$POD_PHASE" != "Succeeded" ]
then
echo "POD enter Failed state"
exit 1;
break;
fi
''')
        }
    }
}

String getParamString(key, value) {
    return '-' + key + ' \${' + key + '} '
}
