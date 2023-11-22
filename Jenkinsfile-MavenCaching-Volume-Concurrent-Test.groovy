def gitMavenRepo = "https://github.com/org-folderscan-example/spring-boot-demo.git"
def pvcMountCacheDir = "/tmp/cache/.m2"
def k8sYaml ="""
    apiVersion: v1
    kind: Pod
    spec:
      containers:
      - name: maven
        image: maven:3.9.5-eclipse-temurin-17-alpine
        command:
        - sleep
        args:
        - infinity
        volumeMounts:
          - name: maven-cache
            mountPath: ${pvcMountCacheDir}
      volumes:
        - name: maven-cache
          persistentVolumeClaim:
            claimName: maven-repo
    """
pipeline {
   agent none
    stages {
        stage ("clean") {
            agent {
                kubernetes {
                    yaml k8sYaml
                    defaultContainer 'maven'
                }
            }
            steps{
                //Clean the cache so we can see concurrent access when it gets refilled
                sh "rm -Rfv ${pvcMountCacheDir}/*"
            }
        }
        stage('BuildAndTest') {
            matrix {
                agent {
                    kubernetes {
                        yaml k8sYaml
                        defaultContainer 'maven'
                    }
                }
                axes {
                    axis {
                        name 'build'
                        //values "build1","build2","build3","build4","build5","build6"
                        values "build1","build2"
                    }
                    axis {
                        name 'localrepo'
                        values "build-A","build-B","build-C","build-D","build-E","build-F"
                    }
                }
                stages {
                    stage('clone') {
                        steps {
                            git gitMavenRepo
                        }
                    }
                    stage('build') {
                        steps {
                            sh "mvn install -Dmaven.repo.local=${pvcMountCacheDir}"
                        }
                    }
                }
            }
        }
        stage ("show-cache"){
            agent {
                kubernetes {
                    yaml k8sYaml
                    defaultContainer 'maven'
                }
            }
            steps {
                sh "ls -ltRa ${pvcMountCacheDir}/de"
            }
        }
    }
}