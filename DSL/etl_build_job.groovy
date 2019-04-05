// create build job for dev environment
String folderName = "1_BUILD_JOB"
String jobName = "0-Build-Etl-Config"

boolean isBuildETLEnv = ["staging"].contains("$ENV_TIER".toLowerCase())

folder(folderName) {
	description('Folder contains job that build the etl image')
}

if(isBuildETLEnv) {
	job(folderName + '/' + jobName) {
		parameters {
			stringParam('TAG_NAME', '0.0.0', '')
		}
		logRotator {
			numToKeep(10)
		}

		scm {
			git {
				remote {
					name('origin')
					url("$BITBUCKET_DSL_REPO")
					credentials('acm_dataplatform')
				}
				branches('master')
			}
		}
		
		steps {
			shell('''\
#!/bin/bash +x
echo "--- Available Tags ---"
for tag_name in $(git tag)
do
  commit=$(git rev-list -n1 ${tag_name} | head -c 8)
  echo "$commit : ${tag_name}"
done
echo "----------------------"
''')
			shell('''\
export ETL_TEMPLATE_FILE=etl-buildconfig-${BUILD_NUMBER}.yaml
#sed "s/%ENV_TIER%/${ENV_TIER}/g" ~/template/etl-buildconfig-template.yaml |sed "s/%TAG_NAME%/${TAG_NAME}/g" > ${ETL_TEMPLATE_FILE}

compile-env.sh ETL/Dockerfile.ejs ETL/Dockerfile

compile-env.sh \\
${JENKINS_HOME}/template/etl-buildconfig-template.yaml \\
${ETL_TEMPLATE_FILE}

echo "=== APPLY BUILD SPEC ==="
cat ${ETL_TEMPLATE_FILE}
echo "========================"
oc apply -f ${ETL_TEMPLATE_FILE}
sleep 5s
echo "=== START BUILD SPEC ==="
oc start-build etl-eq-build --follow

sleep 5s
STATUS=$(oc get build etl-eq-build-1 --template={{.status.phase}})
echo "=== CLEAN UP BUILD ==="
oc delete -f ${ETL_TEMPLATE_FILE}

if [ "$STATUS" = "Complete" ]; then
  echo "build complete."
  exit 0
else
  exit 1
fi

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