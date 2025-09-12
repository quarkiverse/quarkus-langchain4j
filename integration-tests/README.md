# Quarkus LangChain4j Integration Tests

Module with integration tests of the Quarkus LangChain4j project.
By default, the tests are executed against the current project codebase.

Example command:
```
mvn clean verify -f integration-tests -fae
```

## Testing with Quarkus Platform
Quarkus LangChain4j Integration Tests allow execution against a released Quarkus Platform to ensure correct
functionality with dependencies enforced in platform BOMs.

Example command:
```
mvn clean verify -f integration-tests -fae -Dplatform-deps -Dquarkus.platform.version=3.26.3
```

## Running testsuite from main branch
Quarkus LangChain4j modules need to be installed in the local Maven cache because of a trick used with `*-deployment`
artifacts in the integration tests modules `pom.xml` files.
This also applies to the testing with the Quarkus Platform. Running with a tagged and released version of Quarkus
LangChain4j should not require any additional actions like this one.

```
mvn clean install -DskipTests -DskipITs
```

## Dependencies
To allow such a dual mode of test execution, versions of dependencies need to be defined using dependency
management in `integration-tests/pom.xml` and not in the individual test modules.
Dependencies that are not part of Quarkus platform BOM (e.g. `assertj-core`) should be added into dependency
management in the root `pom.xml`.


