#!/bin/bash
project=`echo "${PWD##*/}" | tr '[:upper:]' '[:lower:]'`
project=media-proxy
file="src/main/resources/${project}-git.properties"
org=gennyproject
function prop() {
  grep "${1}=" ${file} | cut -d'=' -f2
}
#version=$(prop 'git.build.version')

if [ -z "${1}" ]; then
  version=$(cat src/main/resources/${project}-git.properties | grep 'git.build.version' | cut -d'=' -f2)
else
  version="${1}"
fi

echo "project = ${project}"
echo "org= ${org}"
echo "version = ${version}"
USER=`whoami`
./mvnw clean package -Dquarkus.container-image.build=true -DskipTests=true
docker tag ${org}/${project}:${version} ${org}/${project}:${version}
docker tag ${org}/${project}:${version} ${org}/${project}:latest
docker tag ${org}/${project}:${version} ${org}/${project}:ptest
