def folderName = "1_BUILD_JOB"
def listItems = [
[name:"1-Update-ETL-Version", jobname: '1-Update-ETL-version', tagName: "latest"]
]
folder(folderName) {
    description('Folder contains all jobs related with image versioning from GCR')
}
for (LinkedHashMap item : listItems){
  def etl_version = item.tagName

  job(folderName+'/'+item.name ) {    
    parameters {
            stringParam('TAG_NAME', etl_version, '')
    }
        
    logRotator {
            numToKeep(14)
    }
    steps {
          shell('''\
#!/bin/bash +x
echo 'Configured version : '+ ${TAG_NAME}
mkdir -p $JENKINS_HOME/image_version
echo ${TAG_NAME}>$JENKINS_HOME/image_version/acm.dp.eq.generic.database.etl.version
export TAG_NAME=$(cat $JENKINS_HOME/image_version/acm.dp.eq.generic.database.etl.version)
''')
    }
  }
}
