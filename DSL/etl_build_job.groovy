// create build job for dev environment
String folderName = "1_BUILD_JOB"
String jobName = "0-Build-Etl-Config"

boolean isDevEnv = "$ENV_TIER".toLowerCase().equals("qa")

folder(folderName) {
  description('Folder contains job that build the etl image')
}

if(isDevEnv) {
  job(folderName + '/' + jobName) {
    parameters {
      stringParam('TAG_NAME', '0.0.0', '')
    }
    logRotator {
      numToKeep(10)
    }
    
    steps {
      shell('''\

sed "s/%ENV_TIER%/${ENV_TIER}/g" ~/template/etl-buildconfig-template.yaml \
|sed "s/%TAG_NAME%/${TAG_NAME}/g" \
> etl-buildconfig-${BUILD_NUMBER}.yaml
echo "=== APPLY BUILD SPEC ==="
cat etl-buildconfig-${BUILD_NUMBER}.yaml
echo "========================"
oc apply -f etl-buildconfig-${BUILD_NUMBER}.yaml
sleep 5s
echo "=== START BUILD SPEC ==="
oc start-build etl-eq-build --follow
sleep 5s
echo "=== CLEAN UP BUILD ==="
oc delete -f etl-buildconfig-${BUILD_NUMBER}.yaml

''')
    }
    publishers {
      downstreamParameterized {
        trigger("1-Update-ETL-Version") {
          condition('UNSTABLE_OR_BETTER')
          parameters {
            predefinedProp("TAG_NAME", "\${TAG_NAME}")
          }
        }
      }
    }

  }
}