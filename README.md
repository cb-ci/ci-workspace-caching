# Maven Caching Options for CI when using PodTemplates

This repo contains `Jenkinsfile-MavenCaching-Test-Parallel.groovy` Pipeline  which shows 3 different ways of dealing with the Maven local repository cache

* Using AWS S3 bucket cache 
  * https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/cloudbees-cache-step#_writecache_step
* Using the Maven default local repository 
  * ~.m2, see https://maven.apache.org/guides/introduction/introduction-to-repositories.html#:~:text=A%20repository%20in%20Maven%20holds,you%20have%20not%20yet%20released
* Using a shared cache volume 
  * Hostpath as used in this test mounts a named directory from the host into the container. Any change made by the container persists until the host is terminated.
    This volume type is risky and can expose the host to attacks from compromised containers; it should only be used in very specific cases.
  * see `create-maven-cache-pvc.yaml`
  * https://github.com/jenkinsci/kubernetes-plugin/blob/master/examples/maven-with-cache.groovy

## Background details

* Workspace Caching:
  * https://github.com/jenkinsci/artifact-manager-s3-plugin#aws-credentials
  * https://docs.cloudbees.com/docs/cloudbees-ci/latest/pipelines/cloudbees-cache-step
  * https://www.cloudbees.com/capabilities/continuous-integration/workspace-caching
  * https://www.cloudbees.com/videos/speeding-up-jenkins-and-maven-build-cache
  * https://www.youtube.com/watch?v=u6LF-T-daS4
  * https://gist.github.com/darinpope/443f1d54b09b914fbeb59e5a12bf6dc1
* Local Repo Locking:
  * https://source.coveo.com/2023/02/28/accelerate-mvn-builds-ci/
  * https://issues.apache.org/jira/browse/MNG-2802
* Other:
  * https://www.jenkins.io/blog/2023/09/06/artifactory-bandwidth-reduction/
  * https://webmasters.stackexchange.com/questions/110310/avoiding-ssl-certificate-errors-with-amazon-s3-subdomain
  * https://sneha-wadhwa.medium.com/speeding-up-ci-pipelines-on-jenkins-63efff817d1d
  * https://codeinfocus.com/blog/2020-04/building-maven-projects-in-jenkins-docker-workers/
  * https://blog.hiya.com/kubernetes-base-jenkins-stateful-agents/
  * https://github.com/jenkinsci/kubernetes-plugin/blob/master/examples/maven-with-cache.groovy

# Pre-requirements

For all the maven cache testing scenarios we will need: 

* [CloudBees CI installation on AWS EKS](https://docs.cloudbees.com/docs/cloudbees-ci/latest/eks-install-guide/)
* CI Controller
* [S3 bucket](https://docs.aws.amazon.com/AmazonS3/latest/userguide/create-bucket-overview.html)
* IAM user with the S3 policies attached (see below)
* A configuration of [Workspace caching in CloudBees CI](https://www.cloudbees.com/capabilities/continuous-integration/workspace-caching) on the CI Controller
* A Maven cache PVC,PV see `create-maven-cache-pvc.yaml` in this repo

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

> kubectl apply -f create-maven-cache-pvc.yaml 

NOTE: For testing purpose the PV uses a [hostpathvolume](https://kubernetes.io/docs/concepts/storage/volumes/#hostpath)
In production you should better use EFS! (ReadWriteMany) 

Hostpath as used in this test mounts a named directory from the host into the container. Any change made by the container persists until the host is terminated.
This volume type is risky and can expose the host to attacks from compromised containers; it should only be used in very specific cases.

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
* The test needs to be done also with a maven pom referencing more (bigger) dependencies.

## Costs 

* S3 is not as expensive rather using EFS
  * https://aws.amazon.com/s3/pricing/
* The price of PV caching depends on the concrete storage (EFS), however, the assumption is that S3 is the most cost-efficient approach
* The maven default repository will download dependencies for each build again from the remote repository.
  * https://www.jenkins.io/blog/2023/09/06/artifactory-bandwidth-reduction/
  * https://aws.amazon.com/ec2/pricing/on-demand/

As of my last knowledge, AWS does not charge specifically for downloading Maven dependencies or any other external internet data into a CI pod within Amazon EKS (Elastic Kubernetes Service).
The costs associated with using EKS are primarily related to the underlying infrastructure (such as EC2 instances, EBS volumes, load balancers), EKS control plane costs, data transfer costs (if applicable), and any additional services or resources utilized within the AWS ecosystem.
Data transfer costs might apply if the CI pod is transferring data outside of the AWS region, across different AWS services, or outside of AWS altogether. However, downloading Maven dependencies from the internet into a CI pod within EKS is not directly charged by AWS.





