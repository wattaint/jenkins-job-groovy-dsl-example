// create build job for dev environment
Utils = evaluate evaluate("""
  new File("$JENKINS_HOME/template/jenkins_script_utils.groovy").text
""")

String latestTagName = Utils.getLatestBuildTagName('latest')
String folderName = "1_BUILD_JOB"
String jobName = "0-Build-Etl-Config"

boolean isBuildETLEnv = Utils.getEtlConfig().build_envs.contains("$ENV_TIER".toLowerCase())

folder(folderName) {
	description('Folder contains job that build the etl image')
}

if(isBuildETLEnv) {
	job(folderName + '/' + jobName) {
		parameters {
			stringParam('TAG_NAME', latestTagName, '')
		}
		logRotator {
			//numToKeep(10)
			daysToKeep(10)
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
export ETL_IMAGE_PROJECT="''' + Utils.getEtlConfig().image_project + '''"
export ETL_APPLICATION="''' + Utils.getEtlConfig().application + '''"
#sed "s/%ENV_TIER%/${ENV_TIER}/g" ~/template/etl-buildconfig-template.yaml |sed "s/%TAG_NAME%/${TAG_NAME}/g" > ${ETL_TEMPLATE_FILE}

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
echo $STATUS

sleep 5s
STATUS=$(oc get build etl-eq-build-1 --template={{.status.phase}})
echo $STATUS

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
