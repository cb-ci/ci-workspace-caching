# Maven Caching Options for CI when using PodTemplates

This repo contains `Jenkinsfile-MavenCaching-Test-Parallel.groovy` Pipeline  which shows 3 different ways of dealing with the Maven local repository cache

* Using AWS S3 bucket
* Using the Maven default setting
* Using a shared volume 


## Background details

* https://github.com/jenkinsci/artifact-manager-s3-plugin#aws-credentials
* https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/cloudbees-cache-step
* https://www.cloudbees.com/capabilities/continuous-integration/workspace-caching
* https://www.cloudbees.com/videos/speeding-up-jenkins-and-maven-build-cache
* https://www.youtube.com/watch?v=u6LF-T-daS4
* https://gist.github.com/darinpope/443f1d54b09b914fbeb59e5a12bf6dc1 
* https://webmasters.stackexchange.com/questions/110310/avoiding-ssl-certificate-errors-with-amazon-s3-subdomain
* https://sneha-wadhwa.medium.com/speeding-up-ci-pipelines-on-jenkins-63efff817d1d

# Pre-requirements

For all the maven cache testing scenarios we will need: 

* A CloudBees CI installation on AWS EKS
* A CI Controller where our `Jenkinsfile-MavenCaching-Test-Parallel.groovy` Pipeline runs
* A S3 Bucket 
* A IAM user with S3 policies attached 
* A configuration of  [Workspace caching in CloudBees CI](https://www.cloudbees.com/capabilities/continuous-integration/workspace-caching) on teh Controller
* A Maven cache PVC,PV 

# S3 Bucket

For the S3 maven caching we need to set up am S3 bucket 

* create [S3 bucket](https://docs.aws.amazon.com/AmazonS3/latest/userguide/create-bucket-overview.html)
* Note: Don't use `.` in the bucket name, it will lead you to SSL cert errors later in the Jenkins AWS setup

## IAM Account

To authenticate from a CI Controller against S3 we need an IAM USer and S3 policies

* create an [IAM User](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_users_create.html) and attach the following policy 

## S3 Bucket Policy

```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListAllMyBuckets"
      ],
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::<YOUR-BUCKET>"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::<YOUR-BUCKET>/*"
    }
  ]
}
```

## Configure CloudBees workspace caching on the CI Controller

For the S3 maven caching we need to set up the workspace caching on a CI Controller

* Setup [Workspace caching in CloudBees CI](https://www.cloudbees.com/capabilities/continuous-integration/workspace-caching) on the CI Controller
* Use the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` to set up a Jenkins AWS credential for the AWS S3 setup

## Create a shared cache volume

For the shared cache volume test we need an shared volume where Maven can store and share the local repository across builds and pods

> kubectl apply -f create-maven-cache-pvc.yml 

* NOTE: For testing purpose the PV uses a [hostpathvolume](https://kubernetes.io/docs/concepts/storage/volumes/#hostpath)
* In production you should better use EFS! (ReadWriteMany) 

# Run the test Pipeline

* Create a Jenkins Pipeline Job with `Jenkinsfile-MavenCaching-Test-Parallel.groovy`

The CasC item looks like this
```
kind: pipeline
name: MavenCacheParallelCompare
concurrentBuild: true
definition:
  cpsScmFlowDefinition:
    scriptPath: Jenkinsfile-MavenCaching-Test-Parallel.groovy
    scm:
      scmGit:
        userRemoteConfigs:
        - userRemoteConfig:
            url: https://github.com/cb-ci/ci-workspace-caching.git
        branches:
        - branchSpec:
            name: '*/main'
    lightweight: true
description: ''
disabled: false
displayName: MavenCacheParallelCompare
resumeBlocked: false
```

* Run the Pipeline and explore time consumption for each build stage using the [Pipeline Explorer](https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/cloudbees-pipeline-explorer-plugin)

![Pipeine explorer](images/pipeline-explorer.png?raw=true "PipelineExplorer")


# Conclusion

## Time consumption

* A Maven cache on a shared volume seems to be the fastest approach.
* It is ~50% faster rather than S3 or Maven default caching
* It has not been tested with Artifactory or Nexus, the default caching in this test uses public Maven repos
* It has not been tested with EFS volumes, it uses "hostpath" volumes in this test

## Costs 

* S3 is not as expensive rather using EFS
* The price of PV caching depends on the concrete storage





