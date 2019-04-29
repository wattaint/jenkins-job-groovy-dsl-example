// import org.yaml.snakeyaml.Yaml

Utils = evaluate evaluate("""
  new File("$JENKINS_HOME/template/jenkins_script_utils.groovy").text
""")

String serviceAccountJson = Utils.getItemList().serviceAccountJson
String secretName = Utils.getItemList().secretName

ArrayList jobList = Utils.getItemList().jobs
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
                if (["trigger_cron", "retry_build"].contains(key)) {
                } else {
                    // def shouldUseDblQuote = [' '].find { item -> value.contains(item) }
                    // if (shouldUseDblQuote)
                    //     value = '\"'+value+'\"'
                        
                    stringParam(key, value, '')

                    if (["podType"].contains(key)) {
                    } else {
                        paramsString += getParamString(key, value)
                    }
                }

                // if (key != "trigger_cron" && key != "retry_build") {
                    
                //     if (value.contains(' '))
                //             value = '\"'+value+'\"'
                            
                //     stringParam(key, value, '')
                    
                //     if (key != "podType"){                        
                //         paramsString += getParamString(key, value)                    
                //     }
                // }
            }
        }
        logRotator {
            daysToKeep(14)
        }
        if (params.trigger_cron != null){
            triggers {
                cron(params.trigger_cron)
            }
        }

        String podTypeString = "default"
        if (params.podType != null){
            podTypeString = params.podType
        }
        
        String podTypeConfigValues = Utils.toJson(Utils.getPodTypeConfigValues(podTypeString))

        steps {
            shell('''\
#!/bin/bash
set -e

SCRIPT_DIR_PATH=${JENKINS_HOME}/template/scripts
export SECRET_TYPE="''' + Utils.getSecretConfig().type + '''"
export ETL_IMAGE_PROJECT="''' + Utils.getEtlConfig().image_project + '''"
export ETL_IMAGE_VERSION_FILE_NAME="''' + Utils.getEtlConfig().image_version_filename + '''"
export ETL_IMAGE_VERSION_FILE_PATH=${JENKINS_HOME}/''' + Utils.getEtlConfig().image_version_dir_name + '''/${ETL_IMAGE_VERSION_FILE_NAME}
export ETL_TEMPLATE_OUTPUT=${WORKSPACE}/etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml
export ETL_TEMPLATE_CONFIG=\'''' + podTypeConfigValues + '''\'
export PARAMS="''' + paramsString.trim() + '''"

if [ -f ${ETL_IMAGE_VERSION_FILE_PATH} ]; then
    etl_tag=$(cat $ETL_IMAGE_VERSION_FILE_PATH)
    if [ -z "${etl_tag}" ]; then
        echo "Empty etl version!, Please re update etl version!"
        exit 255
    else
        export TAG_NAME=${etl_tag}
    fi
else
    echo "ETL verion file not found! Please re update etl verion!"
    echo ${ETL_IMAGE_VERSION_FILE_PATH}
    echo "---------------"
    exit 255
fi

if [ "${SECRET_TYPE}" == "vault" ]; then
  echo "--- do get-vault ---"
  source ${SCRIPT_DIR_PATH}/get-vault.sh
fi

JSON_STRING=$( jq -n \\
                  --arg service_account "''' + serviceAccountJson + '''" \\
                  --arg secret_name "''' + secretName + '''" \\
                  '{SERVICE_ACCOUNT: $service_account, SECRET_NAME: $secret_name}' )

compile-env.sh ${JENKINS_HOME}/template/etl-template.yaml \\
${ETL_TEMPLATE_OUTPUT} \\
"$JSON_STRING"

coffee ${SCRIPT_DIR_PATH}/set-yaml.coffee \\
--in ${ETL_TEMPLATE_OUTPUT} \\
--out ${ETL_TEMPLATE_OUTPUT} \\
--values "${ETL_TEMPLATE_CONFIG}"

cp ${SCRIPT_DIR_PATH}/wait-until-pod.sh wait-until-pod.sh
chmod 755 wait-until-pod.sh

cat ${ETL_TEMPLATE_OUTPUT}

oc create -f ${ETL_TEMPLATE_OUTPUT}
rm -f ${ETL_TEMPLATE_OUTPUT}

# Wait until the pods is running, within 5 mins timeout
POD_NAME=etl-${JOB_BASE_NAME}-${BUILD_NUMBER}

./wait-until-pod.sh $POD_NAME Pending
./wait-until-pod.sh $POD_NAME Running

# Pipe Log to Jenkins Console
oc logs -f ${POD_NAME} || true
''')
            shell('''\
POD_NAME=etl-${JOB_BASE_NAME}-${BUILD_NUMBER}
POD_PHASE=$(oc get po "$POD_NAME" --template={{.status.phase}})
oc describe pod $POD_NAME
oc delete pod $POD_NAME
#rm etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml
if [ "$POD_PHASE" != "Succeeded" ]
then
echo "POD enter Failed state"
exit 1;
break;
fi
''')
        }

    
    if(params.retry_build != null){
        publishers{
             retryBuild {
                retryLimit(params.retry_build.toInteger())
                fixedDelay(300)
              }
        }
       }
    }
}

String getParamString(key, value) {
    return '-' + key + ' \\"\${' + key + '}\\" '
    //return '-' + key + ' \${' + key + '} '
}
