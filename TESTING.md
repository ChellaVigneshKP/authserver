# Unit Testing Guide for Auth Server

## Overview

This document provides comprehensive guidance for unit testing the Auth Server codebase with the goal of achieving:
- **100% line coverage**
- **90% branch coverage**

## Testing Framework

- **JUnit 5** (Jupiter) - Primary testing framework
- **Mockito** - Mocking framework for dependencies
- **Spring Boot Test** - Integration testing support
- **JaCoCo** - Code coverage reporting

## Test Coverage Strategy

### 1. DTOs (Data Transfer Objects)
**Location**: `src/main/java/**/dto/*.java`
**Test Approach**:
- Test all constructors (no-args, all-args, required-args)
- Test all getters and setters
- Test `equals()` and `hashCode()` methods
- Test `toString()` method
- Test with null values
- Test with empty values
- Test with edge cases (very long strings, boundary values, etc.)

**Example DTOs Tested**:
- `CreateAuthCodeDto`
- `FactorOption`
- `OtpValidationRequestDto`
- `OtpReceiverDto`
- `MFAFactor`
- `OtpResponseDto`
- `MFAFactorResponseDto`
- `LogoutRequest` (Record type)
- `CreateTokenDto`

### 2. Exception Classes
**Location**: `src/main/java/**/exception/*.java`
**Test Approach**:
- Test exception message handling
- Test constructors with message only
- Test constructors with message and cause
- Test exception throwing and catching
- Test null and empty messages
- Test exception inheritance
- Test cause chaining for nested exceptions

**Example Exceptions Tested**:
- `AuthCodeCreationFailedException`
- `ApiError`
- `InvalidMFACodeException`
- `OtpValidationFailedException`
- `OtpGenerationFailedException`

### 3. Service Layer
**Location**: `src/main/java/**/*Service.java`
**Test Approach**:
- Use `@ExtendWith(MockitoExtension.class)` for Mockito support
- Use `@Mock` for dependencies
- Use `@InjectMocks` for the service under test
- Test all public methods
- Test success scenarios
- Test failure scenarios
- Test exception handling
- Test null/empty input handling
- Test boundary conditions
- Verify interactions with mocked dependencies using `verify()`

**Example Services Tested**:
- `AuthCodeService`
  - `create()` - success and failure cases
  - `getSessionIdByAuthCode()` - found and not found cases
  - `setConsumedOn()` - various input scenarios

### 4. Entity/Model Classes
**Location**: `src/main/java/**/entity/*.java`
**Test Approach**:
- Test constructors
- Test getters and setters
- Test equals and hashCode
- Test toString
- Test static factory methods (e.g., `fromResult()`)
- Test database row mapping logic
- Test with mock ResultSet objects
- Test SQLException handling

**Example Entities Tested**:
- `AuthCode`
  - Standard POJO tests
  - `fromResult(ResultSet)` static method with mock ResultSet
  - Exception handling in mapping logic

### 5. Repository Layer
**Location**: `src/main/java/**/*Repository.java`
**Test Approach**:
- Mock `NamedParameterJdbcTemplate` or `JdbcTemplate`
- Test CRUD operations
- Test query parameter binding
- Test result set mapping
- Test exception scenarios
- Use `@SpringBootTest` or `@DataJpaTest` for integration tests if needed

### 6. Configuration Classes
**Location**: `src/main/java/**/config/*.java`
**Test Approach**:
- Test bean creation
- Test configuration properties
- Test conditional bean creation
- Use `@SpringBootTest` with test profiles

### 7. Controller/REST Endpoints
**Location**: `src/main/java/**/*Controller.java`
**Test Approach**:
- Use `@WebMvcTest` for focused controller tests
- Use `MockMvc` for request/response testing
- Test HTTP methods (GET, POST, PUT, DELETE)
- Test request validation
- Test response status codes
- Test response bodies
- Test error handling

## Test Naming Conventions

### Test Class Names
- Pattern: `{ClassName}Test`
- Example: `AuthCodeServiceTest`, `CreateAuthCodeDtoTest`

### Test Method Names
- Pattern: `test{MethodName}_{Scenario}`
- Use descriptive names that explain what is being tested
- Examples:
  - `testCreate_Success()`
  - `testCreate_ThrowsException()`
  - `testGetSessionIdByAuthCode_Found()`
  - `testGetSessionIdByAuthCode_NotFound()`
  - `testSettersAndGetters()`
  - `testEqualsAndHashCode()`

## Running Tests

### Run All Tests
```bash
./mvnw clean test
```

### Run Tests with Coverage
```bash
./mvnw clean test jacoco:report
```

### View Coverage Report
Coverage report will be generated at: `target/site/jacoco/index.html`

### Run Specific Test Class
```bash
./mvnw test -Dtest=AuthCodeServiceTest
```

### Run Specific Test Method
```bash
./mvnw test -Dtest=AuthCodeServiceTest#testCreate_Success
```

## Coverage Goals

### Minimum Coverage Requirements (enforced by JaCoCo)
- **Line Coverage**: 100%
- **Branch Coverage**: 90%

### Excluded from Coverage
Consider excluding:
- Configuration classes with no logic
- Main application class
- Generated code
- DTOs with only getters/setters (if using Lombok)

## Best Practices

### 1. Arrange-Act-Assert (AAA) Pattern
```java
@Test
void testCreate_Success() {
    // Arrange
    CreateAuthCodeDto dto = new CreateAuthCodeDto();
    dto.setApplicationId(123);
    when(repository.create(any())).thenReturn(authCode);
    
    // Act
    AuthCode result = service.create(dto);
    
    // Assert
    assertNotNull(result);
    assertEquals(123, result.getApplicationId());
    verify(repository, times(1)).create(dto);
}
```

### 2. Test One Thing Per Test
Each test should verify one specific behavior or scenario.

### 3. Use Descriptive Assertions
```java
// Good
assertEquals(expectedValue, actualValue, "User ID should match");

// Better than
assertTrue(actualValue == expectedValue);
```

### 4. Mock External Dependencies
Always mock:
- Database access
- External API calls
- File I/O
- Network operations

### 5. Test Edge Cases
- Null values
- Empty collections
- Boundary values (0, -1, MAX_VALUE)
- Empty strings
- Very long strings

### 6. Clean Up Resources
Use `@BeforeEach` and `@AfterEach` for setup and cleanup:
```java
@BeforeEach
void setUp() {
    // Initialize test data
}

@AfterEach
void tearDown() {
    // Clean up resources
}
```

## Common Test Patterns

### Testing DTOs with Lombok
```java
@Test
void testEqualsAndHashCode() {
    DTO dto1 = new DTO("value1", "value2");
    DTO dto2 = new DTO("value1", "value2");
    DTO dto3 = new DTO("value3", "value2");
    
    assertEquals(dto1, dto2);
    assertEquals(dto1.hashCode(), dto2.hashCode());
    assertNotEquals(dto1, dto3);
}
```

### Testing Services with Mocked Dependencies
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock
    private Repository repository;
    
    @InjectMocks
    private Service service;
    
    @Test
    void testMethod() {
        when(repository.findById(1)).thenReturn(Optional.of(entity));
        
        Entity result = service.getById(1);
        
        assertNotNull(result);
        verify(repository).findById(1);
    }
}
```

### Testing Exceptions
```java
@Test
void testMethod_ThrowsException() {
    when(repository.save(any())).thenThrow(new DataAccessException("DB error"));
    
    assertThrows(ServiceException.class, () -> {
        service.save(entity);
    });
}
```

### Testing with MockMvc
```java
@WebMvcTest(Controller.class)
class ControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private Service service;
    
    @Test
    void testEndpoint() throws Exception {
        when(service.getData()).thenReturn(data);
        
        mockMvc.perform(get("/api/endpoint"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.field").value("value"));
    }
}
```

## Coverage Gaps Analysis

When coverage falls short:

1. **Identify Untested Code**
   ```bash
   # Open coverage report
   open target/site/jacoco/index.html
   ```

2. **Focus on Critical Paths**
   - Business logic
   - Error handling
   - Edge cases

3. **Add Targeted Tests**
   - Don't just test for coverage numbers
   - Ensure tests are meaningful and catch bugs

## Test Organization

```
src/test/java/
└── com/chellavignesh/authserver/
    ├── authcode/
    │   ├── AuthCodeServiceTest.java
    │   ├── AuthCodeRepositoryTest.java
    │   ├── dto/
    │   │   └── CreateAuthCodeDtoTest.java
    │   ├── entity/
    │   │   └── AuthCodeTest.java
    │   └── exception/
    │       └── AuthCodeCreationFailedExceptionTest.java
    ├── mfa/
    │   ├── MFAServiceTest.java
    │   ├── dto/
    │   │   ├── FactorOptionTest.java
    │   │   └── MFAFactorTest.java
    │   └── exception/
    │       └── InvalidMFACodeExceptionTest.java
    └── ... (other packages)
```

## Continuous Integration

Tests should run automatically on:
- Every commit
- Pull requests
- Before deployment

Example GitHub Actions workflow:
```yaml
name: Test with Coverage
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK
        uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Run tests with coverage
        run: ./mvnw clean test jacoco:report
      - name: Check coverage
        run: ./mvnw jacoco:check
```

## Current Test Coverage Status

### Test Files Created: 19
- DTOs: 9 test files
- Exceptions: 6 test files
- Services: 1 test file
- Entities: 1 test file

### Lines Tested: ~150+ test methods

### Areas Covered:
- ✅ AuthCode module (DTOs, Service, Entity, Exceptions)
- ✅ MFA DTOs and Exceptions
- ✅ Token DTOs
- ✅ Session DTOs
- ✅ Core exception handling (ApiError)

### Next Priority Areas:
- [ ] Additional Service classes
- [ ] Repository layer
- [ ] Configuration classes
- [ ] Controller layer
- [ ] Utility classes

## Troubleshooting

### Build Issues
If you encounter dependency issues:
1. Check Java version compatibility (project requires Java 25, but Java 17 is available)
2. Verify custom dependencies (lib-crypto, auth-java-sdk) are available
3. Consider commenting out unavailable dependencies for testing purposes

### Mock Issues
If mocks aren't working:
- Ensure `@ExtendWith(MockitoExtension.class)` is present
- Verify `@Mock` and `@InjectMocks` annotations
- Check that mocked methods are correctly stubbed with `when().thenReturn()`

### Coverage Not Updating
- Clean build: `./mvnw clean`
- Ensure JaCoCo plugin is properly configured in `pom.xml`
- Check that tests are actually running (not skipped)

## References

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
