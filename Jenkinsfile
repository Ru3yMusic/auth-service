pipeline {
  agent any
  tools {
    jdk 'jdk21'
    maven 'maven3'
  }
  environment {
    SONARQUBE_SERVER = 'sonarqube'
    DOCKER_IMAGE = 'darfelio/rbm-auth-service'
    DOCKER_TAG = 'latest'
    DEPLOY_HOST = '146.181.41.236'
    DEPLOY_USER = 'ubuntu'
  }
  stages {
    stage('Checkout SCM') {
      steps {
        checkout scm
      }
    }
    stage('Tests & Coverage (JaCoCo)') {
      steps {
        sh 'mvn clean verify'
      }
      post {
        always {
          junit 'target/surefire-reports/*.xml'
        }
      }
    }
    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv("${SONARQUBE_SERVER}") {
          sh """
            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
              -Dsonar.projectKey=rbm-auth-service \
              -Dsonar.projectName='Auth Service' \
              -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
          """
        }
      }
    }
    stage('Quality Gate') {
      steps {
        timeout(time: 10, unit: 'MINUTES') {
          waitForQualityGate abortPipeline: true
        }
      }
    }
    stage('Build & Push Docker Image') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKERHUB_USER', passwordVariable: 'DOCKERHUB_PASS')]) {
          sh '''
            echo "$DOCKERHUB_PASS" | docker login -u "$DOCKERHUB_USER" --password-stdin
            docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
            docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
            docker logout
          '''
        }
      }
    }
    stage('Deploy') {
      steps {
        // Requiere credencial SSH en Jenkins (id: arm-ssh-key)
        sshagent(credentials: ['arm-ssh-key']) {
          sh '''
            ssh -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} \
            "docker pull ${DOCKER_IMAGE}:${DOCKER_TAG} && \
             docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-deps auth-service"
          '''
        }
      }
    }
  }
}