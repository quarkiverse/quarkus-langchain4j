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

## Dependencies
To allow such a dual mode of test execution, versions of dependencies need to be defined using dependency
management in `integration-tests/pom.xml` and not in the individual test modules.
 


