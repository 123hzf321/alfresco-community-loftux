#!/bin/bash


#Set the name and build number
versionedition="Community by Loftux AB"
buildnumber="LX82"

# SCM Revision number -Fetch automatically

scmpath=`git config --get remote.origin.url`
scmrevision=`git log --pretty=format:'%h' -n 1`
echo
echo "Loftux Maven Wrapper. Helper script for building Alfresco with maven."
echo
echo "version-edition: $versionedition"
echo "build-number: $buildnumber"
echo "scm-revision: $scmrevision"
echo "scm-path: $scmpath"
echo "maven command: $1"
echo
echo "Press control-c to stop this script."
echo "Press any other key to continue."
read KEY
echo "Starting build..."

mvn clean source:jar $1 -Dversion-edition="$versionedition" -Dbuild-number="$buildnumber" -Dscm-revision="$scmrevision" -Dscm-path="$scmpath"

echo "Restore file deleted by build"
git checkout HEAD projects/surf/spring-surf-tests/spring-surf-fvt-app/src/main/webapp/WEB-INF/presetConstructs/default-persisted-extension.xml
