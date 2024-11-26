while IFS= read -r -d '' pom
do
./mvnw org.codehaus.mojo:versions-maven-plugin:2.16.2:set-property -Dproperty=quarkus-langchain4j.version -DnewVersion=${CURRENT_VERSION}  -f "$(dirname "$pom")";
done  <   <(find samples/ -name pom.xml -print0)
git commit -a -m "Update dependencies in samples"

jbang .github/updateReadme.java "${CURRENT_VERSION}"
git commit -a -m "Update README to ${CURRENT_VERSION}"
