pipeline {

    /*
    Definition where the agent will run, for this time it is running in the Master server
     */
    agent {
        label 'master'
    }

    /*
    Any environment variable need it must be here
     */
    environment {
        GIT_URL = ""
    }

    stages {
        stage('api-selection') {
            steps {
                script {
                    try {
                        
                        def props = readProperties  file: "/opt/apache-tomcat/jenkins-conf/${groupServices}.properties"
                        servicesInFile = readFile  file: "/opt/apache-tomcat/jenkins-conf/${groupServices}.properties"

                        echo "${servicesInFile}"

                        // Display options
                        String optionSelected = input message: '', parameters: [choice(choices: "${servicesInFile}", description: 'Select a service to deploy', name: 'Options')]

                        // We read the GIT url from they property object
                        String apiGitURL = optionSelected.split("=")[1]
						String serviceAPI = optionSelected.split("=")[0]
						
						                        // we set a proper display name to the service, in order to allow us to
                        // identify it correctly                    
                        currentBuild.displayName = "${envP}_${branch}_${serviceAPI}"
                        currentBuild.description = currentBuild.displayName

                        // Logging parameters
                        //echo "*** API Selected: ${API}"
                        echo "*** env: ${envP}"
                        echo "*** branch: ${branch}"
                        echo "*** apiGitURL: ${apiGitURL}"
                        
                        
                        if(apiGitURL != null) {
                            // build job
                            build job: 'api-pipeline', parameters: [string(name: 'branch', value: "${branch}"), string(name: 'gitURL', value: "${apiGitURL}"), string(name: 'envTarget', value: "${envP}")]

                        } else {
                            // error, not found API configured
                            currentBuild.displayName = "${envP}_${branch}_${API} error: not found"
                            currentBuild.description = currentBuild.displayName
                            error "ERROR: API was not found"
                        }
                    } catch(Exception e) {
                        echo "Exception: ${e}"
                        error "ERROR: exception: ${e}"
                    }
                }
            }
        }        
    }
}
