
Utils = evaluate evaluate("""
  new File("$JENKINS_HOME/template/jenkins_script_utils.groovy").text
""")

jobFullName = 'Tools/Update_GCS_Logging'
job(jobFullName) {
  //triggers {
  //  cron("H * * * *")
  //}
  steps {
    systemGroovyCommand('''
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*;
import com.cloudbees.plugins.credentials.impl.*;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig
import java.io.FileInputStream
import jenkins.model.*
import org.apache.commons.fileupload.FileItem

import jenkins.model.Jenkins
import hudson.model.*
import jenkinsci.plugins.influxdb.InfluxDbPublisher
import com.tikal.jenkins.plugins.multijob.*
import com.tikal.jenkins.plugins.multijob.PhaseJobsConfig.*

import com.google.jenkins.plugins.storage.*

def credId = getBinding().getVariables()['GoogleCredId']
def domain = Domain.global()
def systemCredentialsProvider = SystemCredentialsProvider.getInstance()

systemCredentialsProvider.getCredentials().each {
  def cred = (com.cloudbees.plugins.credentials.Credentials) it
  println(cred.getId())
  if ( cred.getId() == credId ) {
    println("[Credential]Found existing credentials: " + credId)
    systemCredentialsProvider.removeCredentials(domain, it)
    println("[Credential]" + credId + " is removed and will be recreated..")
  }
}

def binding = getBinding().getVariables()
println(binding)

def filey = new FileInputStream('/opt/service-accounts/gcp-gcs.json')
fileItem = [ getSize: { return 1L }, getInputStream: { return filey } ] as FileItem

def ServiceAccount = new JsonServiceAccountConfig(fileItem, null)
def GoogleAccount  = new GoogleRobotPrivateKeyCredentials(credId, ServiceAccount, null)
systemCredentialsProvider.addCredentials domain, GoogleAccount

// -----------------------------
def bucketName = getBinding().getVariables()['bucketName']

def bucketFullName = "gs://${bucketName}/jenkins-stdouts/\\$JOB_NAME/\\$BUILD_NUMBER"
def stdoutLog = "build-log.txt"

def stdUpload = new StdoutUpload(bucketFullName, null, stdoutLog, null)
stdUpload.setForFailedJobs true
stdUpload.setShowInline true

// def expiring = new ExpiringBucketLifecycleManager(bucketFullName, null, 30, null, null)
// def uploads = [ stdUpload, expiring ]
def uploads = [ stdUpload ]

def newGcsPublisher = new GoogleCloudStorageUploader(credId, uploads)
Jenkins.instance.getAllItems(AbstractProject.class).each { job ->
  def publishersList = job.getPublishersList()
  publishersList.removeAll {
    it.getClass().getName() == 'com.google.jenkins.plugins.storage.GoogleCloudStorageUploader' 
  }
  job.save()
  
  if( ["0-init-etl-template"].contains(job.getName()) ) {
    println "--- [Skip] gcs-log-upload ---> " + job.getFullName()
    return
  }
  println "--- [Add ] gcs-log-upload ---> " + job.getFullName()
  publishersList.add newGcsPublisher
  job.save()
}

    ''') {
      binding("AA", "$HOME")
      binding("bucketName", Utils.getBucketName())
      binding("GoogleCredId", "gcp-gcs")
      binding("gcsProjectId", Utils.getGcsServiceAccountData().project_id)
    }
  }
}

Utils.addTriggerByAfterRun('Tools/after_init', jobFullName)

