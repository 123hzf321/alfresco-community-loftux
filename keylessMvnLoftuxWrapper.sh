#!/bin/bash


#Set the name and build number
versionedition="Community by Loftux AB"
buildnumber="5.0.a.LX66"

# SCM Revision number -Fetch automatically

scmpath=`git config --get remote.origin.url`
scmrevision=`git describe --tags --always HEAD`
echo
echo "Loftux Maven Wrapper. Helper script for building Alfresco with maven."
echo
echo "version-edition: $versionedition"
echo "build-number: $buildnumber"
echo "scm-revision: $scmrevision"
echo "scm-path: $scmpath"
echo "maven command: $1"

mvn clean $1 -Dversion-edition="$versionedition" -Dbuild-number="$buildnumber" -Dscm-revision="$scmrevision" -Dscm-path="$scmpath"
