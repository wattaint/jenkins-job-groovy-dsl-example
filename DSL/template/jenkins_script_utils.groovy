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

etl_image_conf = """
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

etl_image_confEval = null
def getEtlImageConf() {
    if (etl_image_confEval == null){
        println("-- get etl_image_conf --")
        etl_image_confEval = evaluate(etl_image_conf)
    }
    return etl_image_confEval
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

return this