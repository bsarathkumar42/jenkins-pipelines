pipeline {

    /*
    Definition where the agent will run, for this time it is running in the Master server
     */
    agent {
        label 'ec2'
    }

    parameters {
        /*Parameters of Job*/
        string(defaultValue: 'master', description: '', name: 'branch')
        string(defaultValue: 'https://github.com/danielalejandrohc/spring-boot-poc-test.git', description: '', name: 'gitURL')
        string(defaultValue: 'dev', description: '', name: 'envTarget')
    }

    /*
    //    Any environment variable need it must be here
    
    environment {
    }
     */

    stages {
        stage('sscm clone && compile') {
            steps {

                // In this moment we can defined which env of configuration we should use
                script {
                    deleteDir();
                    git url: "${gitURL}", branch: "${branch}";
                    String folderEnv = "src/main/resources/${envTarget}";
                    String targetFolder = "src/main/resources/"
                    
                    sh "mkdir ${envTarget}"
                    sh "cp src/main/resources/${envTarget}/* ${envTarget}"
                    sh "rm -r src/main/resources/*"
                    sh "cp ${envTarget}/* src/main/resources"
                }
            }
        }

        stage('jar build') {
            steps {
                // Create .jar file, skipping the test since we will do kind of smoke test after deployment
                sh "mvn package -Dmaven.test.skip=true -DenvP=${envTarget}"
            }
        }

        stage('create docker image') {
            steps {
                //sh "mvn install dockerfile:build"
                script {
                    // read the pom.xml in order to read it attributes
                    def pom = readMavenPom file: "pom.xml"
                    // The artifactID it is the attr. we will use to identify the Docker Image
                    artifactId = pom.artifactId
                    version = pom.version
                    echo "artifactId: ${artifactId}"
                    // Create a copy of the jar getting rid of the version
                    sh "cp target/${artifactId}-${version}.jar target/${artifactId}.jar"
                    // We will build the docker image with the ID we use in the pom.xml file
                    def app = docker.build "${envTarget}-${artifactId}/${env.BUILD_TAG}"
                }
            }
        }

        stage('deploy') {
            steps {
                script {
                    try {
                        // Parameter -a it will run containers that even stopped, for instance when you shoutdown 
                        // the docker it is not configured to run startup containers after these reboots
                        containerId = sh (script: "docker ps -q -a -f name=${envTarget}-${artifactId}", returnStdout: true)
                        echo "containerId: ${containerId}"
                        sh "docker stop  ${containerId}"
                        sh "docker rm  ${containerId}"
                    } catch(Exception e) {
                        echo "Error: ${e}"
                    }

                    
                    // read the port information in order to redirect it
                    def datas = readYaml file: "src/main/resources/application.yml"
                    port = datas['server']['port']
                    echo "Port: ${port}"

                    // we will forward local port to the port of the docker image
                    // for instace 8090 -> 8090
                    sh "docker run -p ${port}:${port} --name ${envTarget}-${artifactId} -t ${envTarget}-${artifactId}/${env.BUILD_TAG}   &"                    
                }
            }
        }

        stage('smoke test') {
            steps {
                sh "mvn test -DenvP=${envTarget}"
                mail bcc: '', body: 'Testing passed!', cc: '', from: '', replyTo: '', subject: "${env.BUILD_TAG} - Test result: passed", to: 'danielahcardona@gmail.com'
            }
        }
        
    }

    

    post { 
        failure { 
            mail bcc: '', body: "Check  ${env.BUILD_TAG}", cc: '', from: '', replyTo: '', subject: "${env.BUILD_TAG} - Error", to: 'danielahcardona@gmail.com'
        }
    }
}


