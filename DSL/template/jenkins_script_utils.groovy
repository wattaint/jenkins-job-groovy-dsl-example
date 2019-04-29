import groovy.json.*
import jenkins.model.Jenkins
import hudson.model.*

itemList = """
@Grab('org.yaml:snakeyaml')
import org.yaml.snakeyaml.Yaml
Yaml parser = new Yaml()
def itemList = parser.load(("${WORKSPACE}/${CHECKOUT_PATH}/${ENV_TIER}/jobs.yaml" as File).text)
println "========== jobs.yaml ============"
Yaml yaml = new Yaml()
output = yaml.dump(itemList)
println output
println "=================================="
return itemList
"""

jsonConf = """
import groovy.json.JsonSlurper
println "========== Load JSON config ============"
def inputFile = new File("${WORKSPACE}/${CHECKOUT_PATH}/config.json")
def InputJSON = new JsonSlurper().parseText(inputFile.text)
println InputJSON.each{ println it }
println "=================================="
return InputJSON
"""

gcsServiceAccountData = """
import groovy.json.JsonSlurper
println "=== Get service account file data ==="
def inputFile = new File("/opt/service-accounts/gcp-gcs.json")
def InputJSON = new JsonSlurper().parseText(inputFile.text)
println "  : " + InputJSON
return InputJSON
"""

bucketName = """
@Grab('org.yaml:snakeyaml')
import org.yaml.snakeyaml.Yaml

println "=== Get Bucket Name ==="
Yaml parser = new Yaml()
def itemList = parser.load(("${WORKSPACE}/${CHECKOUT_PATH}/${ENV_TIER}/jobs.yaml" as File).text)

def bucketName = null
ArrayList jobList = itemList.jobs
def firstJobConfig = null
for(LinkedHashMap item in jobList) {
  if ( bucketName ) { continue; }

  item.each { jobGroupName, jobDetails ->
    if (bucketName) { return }

    jobDetails.each { job ->
      if (bucketName) { return }

      if (job instanceof LinkedHashMap ) {
        job.each { jobKey, jobConfig ->
          if (bucketName) { return }

          bucketName = jobConfig['bucketName']
        }
      }
    }

  }
}
println "  : " + bucketName
return bucketName
"""

itemListEval = null
def getItemList(){
    if (itemListEval == null) {
        println("-- get item lists --")
        itemListEval = evaluate(itemList)
    }
    return itemListEval
}

jsonConfEval = null
def getEtlConfig() {
    if (jsonConfEval == null){
        jsonConfEval = evaluate(jsonConf)
    }
    return jsonConfEval.etl
}

def getSecretConfig() {
  if (jsonConfEval == null){
        jsonConfEval = evaluate(jsonConf)
    }
    return jsonConfEval.secret
}

gcsServiceAccountDataEval = null
def getGcsServiceAccountData() {
    if ( gcsServiceAccountDataEval == null){
        println('-- get gcsServiceAccountData --')
        gcsServiceAccountDataEval = evaluate(gcsServiceAccountData)
    }
    return gcsServiceAccountDataEval
}

getBucketNameEval = null
def getBucketName() {
    if (getBucketNameEval == null) {
        println('-- get getBucketName --')
        getBucketNameEval = evaluate(bucketName)
    }
    return getBucketNameEval
}

String getLatestBuildTagName(String defaultTagName = '0.0.0' ) {
  def etl = getEtlConfig()
  def latest = defaultTagName
  versionfile = new File("${JENKINS_HOME}/${etl.image_version_dir_name}/${etl.image_version_filename}")
  if (versionfile.exists() && versionfile.canRead()) {
    latest = versionfile.text
  } else {
    latest = defaultTagName
  }

  return latest

}

String toJson(obj) {
  new JsonBuilder( obj ).toString()
}

def getPodTypeConfigValues(name) {
  def podTypes = getEtlConfig().templates
  def defaultPodType = podTypes['default']
  def confPodType = podTypes[name]
  if (confPodType) {
    return confPodType 
  } else {
    return defaultPodType
  }
}

def addTriggerByAfterRun(upstreamJobFullName, jobStatFullName) {
    println("-- [${jobStatFullName}] -- checking downstream for ${upstreamJobFullName}")
    def afterInitJob = Jenkins.instance.getAllItems(AbstractProject.class).find {
        [upstreamJobFullName].contains(it.fullName)
    }
    if (afterInitJob) {
        def selfJob = Jenkins.instance.getAllItems(AbstractProject.class).find {
            [jobStatFullName].contains(it.fullName)
        }
        if (selfJob) {
            def publishersList = afterInitJob.getPublishersList()
            def exists = publishersList.find { 
                if (it.getClass().getName() == 'hudson.tasks.BuildTrigger') {
                    return [jobStatFullName].contains(it.getChildProjectsValue())
                }
                return false
            }
            if (exists) {
                println("-- [${jobStatFullName}] -- skip downstream for ${upstreamJobFullName}")
            } else {
                println("-- [${jobStatFullName}] -- add downstream for ${upstreamJobFullName}")
                def trigger = new hudson.tasks.BuildTrigger(jobStatFullName, true)
                publishersList.add(trigger)
                afterInitJob.save()
            }
        }
    }
}

return this
