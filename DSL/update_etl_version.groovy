Utils = evaluate evaluate("""
  new File("$JENKINS_HOME/template/jenkins_script_utils.groovy").text
""")

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

export ETL_IMAGE_VERSION_FILE_NAME="'''+Utils.getEtlImageConf().etl_image_version_filename+'''"
export ETL_IMAGE_VERSION_FILE_PATH=${JENKINS_HOME}/'''+Utils.getEtlImageConf().etl_image_dir+'''/${ETL_IMAGE_VERSION_FILE_NAME}

echo 'Configured version : '+ ${TAG_NAME}

mkdir -p $(dirname ${ETL_IMAGE_VERSION_FILE_PATH})
echo ${TAG_NAME} > ${ETL_IMAGE_VERSION_FILE_PATH}
export TAG_NAME=$(cat ${ETL_IMAGE_VERSION_FILE_PATH})
''')
    }
  }
}
