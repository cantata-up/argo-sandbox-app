// Application Info
def appName = "sandbox-frontendapp"
def appBranch = "master"


// ArgoCD Info
def argoName = "argo-sandbox-app"
def argoBranch = "master"
def argoUrl = "argocd-dashboard.apps.hqokd.nshcloud.com"

// Git Info
def appGitUrl = "gitlab.hqokd.nshcloud.com/jyjeong/${appName}"
def argoGitUrl = "gitlab.hqokd.nshcloud.com/jyjeong/${argoName}"


// Container Registry Info
def harborUrl = "harbor.hqokd.nshcloud.com"


// Container Image Registry
def containerImageRegistry = "${harborUrl}/${appName}/${appName}:${TAG}"



///////////////////////////////////////////////////////////////////////////////////////////// 


pipeline {
    environment {      
        PATH="$PATH:/usr/local/bin/"
        harborId = credentials('harbor-id')
        harborPw = credentials('harbor-pw')
        argoId = credentials('argocd-id')
        argoPw = credentials('argocd-pw')
    }
  agent any   
  
  stages {
        stage('workspace clear1'){
            steps {
                cleanWs()
            }
        }
        stage('Git Clone Applicationt') {           
            steps {                  
                checkout scm: [
                    $class: 'GitSCM', 
                    userRemoteConfigs: [[url: "http://${appGitUrl}", credentialsId: 'gitlab-repo' ]], 

                     branches: [[name: 'refs/tags/${TAG}']]],
                     poll: false
                }
        }
        
        stage('Build') {           
            steps {
                sh "npm install"
            }
        }      
     
        stage('Building image') {
            steps {  
                sh "podman build -t ${containerImageRegistry} ."                
            }
        }

		stage('Deploy Image') {
          steps{
            script {
                    sh "podman login -u $harborId -p $harborPw ${harborUrl}"
                    sh "podman push ${containerImageRegistry}"
                }
            }
          }      

        stage('workspace clear2'){
            steps {
                cleanWs()
            }
        }
        stage('GitOps Build') {   
            steps{
                print "======kustomization.yaml tag update====="
                // git branch: "${argoBranch}", url: "http://${argoGitUrl}", credentialsId: "gitlab-repo"     
                git url: "http://${argoGitUrl}", credentialsId: "gitlab-repo"
                script{
                    def cmd = "cd ${appName} && awk '/newTag: /{print  \$2}' ./kustomization.yaml"                    
                    def originStr =  executeCmdReturn(cmd)
                    if(!originStr){
                        currentBuild.result = 'FAILURE'
                        return
                    }   

                    def originTag= originStr.replaceAll('"','')
                    sh "cd ${appName} && sed -i.bak 's/${originTag}/${TAG}/g' ./kustomization.yaml"                    
                    sh "cd ${appName} && rm ./kustomization.yaml.bak"

                   // sh "git add ./${argoName}; git commit -m 'trigger generated tag : ${TAG}' "
                    sh("cd ${appName} && git add .; git commit -m 'trigger generated tag : ${TAG}  ' ")

                    withCredentials([usernamePassword(credentialsId: 'gitlab-repo', usernameVariable: 'username', passwordVariable: 'password')]){                    
                        sh("git push http://$username:$password@${argoGitUrl}  ${argoBranch}")
                    }
                 } 
                print "git push finished !!!"
            }
        }
        
        stage('argocd sync') {           
            steps { 
                script{
                    sh "argocd login --username $argoId --password $argoPw ${argoUrl} --insecure"
                    sh "argocd app sync ${appName} "
                }
            }
        }          
        
        
  }
}

def  executeCmdReturn(cmd){
  return sh(returnStdout: true, script: cmd).trim()
}

def replaceText(str){
    return str.replaceAll('"','')
}