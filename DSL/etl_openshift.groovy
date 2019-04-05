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
etl_image_conf = evaluate("""
    import groovy.json.JsonSlurper
    println "========== Load JSON config ============"
    def inputFile = new File("$WORKSPACE/$CHECKOUT_PATH/config.json")
    def InputJSON = new JsonSlurper().parseText(inputFile.text)
    println InputJSON.each{ println it }
    println "=================================="
    return InputJSON
""")

String serviceAccountJson = itemList.serviceAccountJson
String secretName = itemList.secretName

ArrayList jobList = itemList.jobs
createDirJobs(null, jobList, serviceAccountJson, secretName)
createJobStat()

def createJobStat() {
    folder('Tools') {
        description('Folder contains monitoring or utils jobs')
    }
    job('Tools/stats') {
        steps {
            shell('''\
export ETL_IMAGE_VERSION_FILE_NAME="'''+etl_image_conf.etl_image_version_filename+'''"
export ETL_IMAGE_VERSION_FILE_PATH=${JENKINS_HOME}/'''+etl_image_conf.etl_image_dir+'''/${ETL_IMAGE_VERSION_FILE_NAME}
python ${JENKINS_HOME}/template/ocp-job-stats.py
            ''')
        }

        triggers {
            cron("*/30 * * * *")
        }
    }
}

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
                    paramsString += getParamString(key, value)                    
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
        steps {
            shell('''\
#!/bin/bash
set -e

export ETL_IMAGE_VERSION_FILE_NAME="''' + etl_image_conf.etl_image_version_filename + '''"
export ETL_IMAGE_VERSION_FILE_PATH=${JENKINS_HOME}/'''+etl_image_conf.etl_image_dir+'''/${ETL_IMAGE_VERSION_FILE_NAME}
export ETL_TEMPLATE_OUTPUT=etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml
export PARAMS="'''+paramsString.trim()+'''"
# Escape "/" -> "\\/" and "&" -> "\\&" charactor
export ESC_PARAMS=`echo $PARAMS |sed -e 's#&#\\\\\\\\\\\\\\\\\\\\\\\\&#g' |sed -e 's#/#\\\\\\/#g'`
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

#  sed "s/%BUILD_NUMBER%/${BUILD_NUMBER}/g" ~/template/etl-template.yaml \\
#  |sed "s/%JOB_BASE_NAME%/${JOB_BASE_NAME}/g" \\
#  |sed "s/%SERVICE_ACCOUNT%/'''+serviceAccountJson+'''/g" \\
#  |sed "s/%SECRET_NAME%/'''+secretName+'''/g" \\
#  |sed "s/%TAG_NAME%/${TAG_NAME}/g" \\
#  |sed "s/%ENV_TIER%/${ENV_TIER}/g" \\
#  |sed "s/%PARAMS%/$ESC_PARAMS/g" \\
#  > etl-${JOB_BASE_NAME}-${BUILD_NUMBER}.yaml

JSON_STRING=$( jq -n \\
                  --arg service_account "''' + serviceAccountJson + '''" \\
                  --arg secret_name "''' + secretName + '''" \\
                  '{SERVICE_ACCOUNT: $service_account, SECRET_NAME: $secret_name}' )

compile-env.sh ${JENKINS_HOME}/template/etl-template.yaml \\
${ETL_TEMPLATE_OUTPUT} \\
"$JSON_STRING"

cp ${JENKINS_HOME}/template/wait-until-pod.sh wait-until-pod.sh
chmod 755 wait-until-pod.sh

cat ${ETL_TEMPLATE_OUTPUT}

oc create -f ${ETL_TEMPLATE_OUTPUT}

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
