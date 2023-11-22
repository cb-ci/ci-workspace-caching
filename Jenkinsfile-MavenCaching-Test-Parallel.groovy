def gitMavenRepo = "https://github.com/org-folderscan-example/spring-boot-demo.git"
def s3CacheDir = "maven-cache"
def pvcMountCacheDir = "/tmp/cache/.m2"
def k8sYaml = """
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
        stage("Test-maven-caches") {
            parallel {
                stage("maven-S3-cache") {
                    agent {
                        kubernetes {
                            yaml k8sYaml
                            defaultContainer 'maven'
                        }
                    }
                    stages {
                        stage("clone") {
                            steps {
                                git gitMavenRepo
                            }
                        }
                        stage("build") {
                            steps {
                                echo """
                                        This will use the local -Dmaven.repo.local=./${s3CacheDir} dir inside the agent workspace \n
                                        Before, Maven dependencies will be downloaded from AWS S3 cache for each new build to ./${s3CacheDir} 
                                    """
                                readCache name: s3CacheDir
                                sh "mvn clean install -Dmaven.repo.local=./${s3CacheDir} -DskipTests"
                                //archive to S3
                                //archiveArtifacts "target/*.war"
                                writeCache name: s3CacheDir, includes: "${s3CacheDir}/**", excludes: "**SNAPSHOT**"
                            }
                        }
                    }
                }
                stage("maven-default-cache") {
                    agent {
                        kubernetes {
                            yaml k8sYaml
                            defaultContainer 'maven'
                        }
                    }
                    stages {
                        stage("clone") {
                            steps {
                                git gitMavenRepo
                            }
                        }
                        stage("build") {
                            steps {
                                echo """
                                        This will use the local .m2 dir (Maven default) inside the pod \n
                                        Maven dependencies will be downloaded from the remote maven repository for each new build
                                    """
                                sh "mvn clean install -DskipTests"
                                //archiveArtifacts "target/*.war"
                            }
                        }
                    }
                }
                stage("maven-pvc-mount-cache") {
                    agent {
                        kubernetes {
                            yaml k8sYaml
                            defaultContainer 'maven'
                        }
                    }
                    stages {
                        stage("clone") {
                            steps {
                                git gitMavenRepo
                            }
                        }
                        stage("build") {
                            steps {
                                echo """
                                        This will use the -Dmaven.repo.local=${pvcMountCacheDir} which is a mounted voume.\n
                                        For each new build, maven references to the mounted voulume first and tries to resolve dependencies from there
                                    """
                                sh "mvn clean install -Dmaven.repo.local=${pvcMountCacheDir}  -DskipTests"
                                //archiveArtifacts "target/*.war"
                            }
                        }
                    }
                }
            }
        }
    }
}