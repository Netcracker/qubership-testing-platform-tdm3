#!/usr/bin/env sh

set -e

mkdir -p helm-templates/atp3-tdm-be
cp -R ./deployments/charts/atp3-tdm-be/* helm-templates/atp3-tdm-be

zip -r source_git.zip . -x '\.*' -x '*/node*'

# Enable dev fast build mode that disable checkstyle, pmd, spotbug, dtrust validations and tests
# Must be disabled by default (false value)!
echo "ATP_DEV_FAST_BUILD = ${ATP_DEV_FAST_BUILD:=false}"

BRANCH_NAME="$(git rev-parse --abbrev-ref HEAD)"
echo "Branch name: ${BRANCH_NAME:?Cannot get branch name}"
VERSION="$(grep -wm1 'VERSION:' description.yaml | cut -d ':' -f2 | tr -d " '\"")"
echo "Description version: ${VERSION:?Cannot read version from description.yaml}"

if echo "${BRANCH_NAME}" | grep -qvE "^(master|development)$"; then
  TICKET_ID="$(echo "${BRANCH_NAME}" | grep -Eo '(PSUP)?ATPIII?-[0-9]{1,10}')"
  echo "TicketID: ${TICKET_ID:?Branch name must contain a valid ticket ID}"
  VERSION="${VERSION}-${TICKET_ID}"
  echo "New package version: ${VERSION:?}"
fi

mvn -q org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnewVersion=${VERSION}

mkdir -p clover_archive \
         test_archive/qubership-atp-tdm-backend \
         deployments/update \
         dist/atp \
         build

if [ "${ATP_DEV_FAST_BUILD}" = "false" ]; then
  echo "=> Starting maven build"
  #MAVEN BUILD
  mkdir -p ./database
  mvn clean dependency:tree verify -P with-analyzers \
    com.netcracker.om.tls.maven.plugin:dtrust-maven-plugin:4.2.2:dependencies \
    -Ddtrust.approve.build.fail=false

  ls -lha .
  echo './DATABASE'
  ls -lha ./database

  mvn --show-version -B deploy -P\!front-end -DskipTests \
    com.netcracker.om.tls.maven.plugin:dtrust-maven-plugin:4.2.2:dependencies \
    -Ddtrust.approve.build.fail=false
#    -DJDBC_URL="jdbc:h2:file:./database/atptdm;DB_CLOSE_ON_EXIT=FALSE"


  cp -a qubership-atp-tdm-backend/target/clover/. clover_archive
  cp qubership-atp-tdm-backend/target/pmd/*.xml test_archive/qubership-atp-tdm-backend
  cp qubership-atp-tdm-backend/target/checkstyle/checkstyle-result.xml test_archive/qubership-atp-tdm-backend
  cp qubership-atp-tdm-backend/target/spotbugs/xml/*.xml test_archive/qubership-atp-tdm-backend

  cd test_archive || exit 1
  zip -r TEST_RESULTS.zip .
  cd ..

  cd clover_archive || exit 1
  zip -r CLOVER_RESULTS.zip .
  cd ..
else
  mvn --show-version -B deploy -DskipTests
  touch clover_archive/CLOVER_RESULTS.zip test_archive/TEST_RESULTS.zip
fi

#DOCKER BUILD
echo "=> Starting docker build"
unzip -oq qubership-atp-tdm-distribution/target/*.zip -d build/

for docker_image_name in ${DOCKER_NAMES}; do
  DOCKER_BUILDKIT=1 docker build \
    --build-arg HTTP_FTP_PROJECTS=${HTTP_FTP_PROJECTS} \
    --pull \
    -t ${docker_image_name} \
    .
done
