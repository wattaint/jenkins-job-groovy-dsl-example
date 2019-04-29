Utils = evaluate evaluate("""
  new File("$JENKINS_HOME/template/jenkins_script_utils.groovy").text
""")

def createJobStat(jobStatFullName) {
    folder('Tools') {
        description('Folder contains monitoring or utils jobs')
    }

    job(jobStatFullName) {
        steps {
            shell("""
export ETL_BUCKET_NAME="${Utils.getBucketName()}"
export ETL_PROJECT_ID="${Utils.getGcsServiceAccountData().project_id}"
export ETL_CLIENT_EMAIL="${Utils.getGcsServiceAccountData().client_email}"
export ETL_IMAGE_VERSION_FILE_NAME="${Utils.getEtlConfig().image_version_filename}"
export ETL_IMAGE_VERSION_FILE_PATH="${JENKINS_HOME}/${Utils.getEtlConfig().image_version_dir_name}/${Utils.getEtlConfig().image_version_filename}"
python \${JENKINS_HOME}/template/scripts/ocp-job-stats.py
""")
        }

        triggers {
            cron("H * * * *")
        }
    }
}

jobStatFullName = "Tools/stats"
createJobStat(jobStatFullName)
Utils.addTriggerByAfterRun('Tools/after_init', jobStatFullName)