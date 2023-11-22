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
#        securityContext:
#          runAsUser: 1000
        volumeMounts:
          - name: maven-cache
            mountPath: ${pvcMountCacheDir}
      volumes:
        - name: maven-cache
          persistentVolumeClaim:
            claimName: maven-repo
    """
pipeline {
    agent {
        kubernetes {
            yaml k8sYaml
            defaultContainer 'maven'
        }
    }
    stages {
        stage ("clean") {
            steps{
                //Clean the cache so we can see concurrent access when it gets refilled
                echo "clean before"
                sh "rm -Rfv ${pvcMountCacheDir}/*"
            }
        }
        stage('BuildAndTest') {
            matrix {
                axes {
                    axis {
                        name 'build'
                        //values "build-1","build-2","build-3","build-4","build-5","build-6"
                        values "build-1","build-2"
                    }
                    axis {
                        name 'localrepo'
                        values "build-A","build-B","build-C","build-D","build-E","build-F"
                    }
                }
                stages {
                    stage('build') {
                        steps {
                            dir ("${build}_${localRepo}") {
                                git gitMavenRepo
                                //This might break the builds in other threads
                                //sh "rm -Rfv ${pvcMountCacheDir}/*"
                                sh "mvn install -Dmaven.repo.local=${pvcMountCacheDir}"
                            }
                        }
                    }
                }
            }
        }
        stage ("show-cache"){
            steps {
                sh "ls -ltRa ${pvcMountCacheDir}/de"
            }
        }
    }
}