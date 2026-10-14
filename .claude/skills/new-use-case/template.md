# Skeletons for the new-use-case skill

Replace `Example`, the inputs and the messages. Keep the shape.

## Use case (`app/src/main/java/com/portfolioiq/domain/usecase/ExampleUseCase.java`)

```java
package com.portfolioiq.domain.usecase;

import com.portfolioiq.domain.repository.ExampleRepository;
import com.portfolioiq.domain.repository.ResultCallback;

/**
 * Story NNN: <one line: the user action>. Raw strings in, validated here,
 * then delegated to ExampleRepository (CLAUDE.md, UseCase convention).
 */
public class ExampleUseCase {

    private final ExampleRepository exampleRepository;

    public ExampleUseCase(ExampleRepository exampleRepository) {
        this.exampleRepository = exampleRepository;
    }

    public void execute(String userId, String rawInput, ResultCallback<ResultType> callback) {
        if (userId == null || userId.trim().isEmpty()) {
            callback.onError("You're signed out. Please sign in again.");
            return;
        }
        if (rawInput == null || rawInput.trim().isEmpty()) {
            callback.onError("<exact message the user sees>");
            return;
        }
        // ...more checks, in order, each returning on failure...

        String normalized = rawInput.trim();
        exampleRepository.doThing(userId.trim(), normalized, callback);
    }
}
```

## Repository contract (`domain/repository/ExampleRepository.java`)

```java
package com.portfolioiq.domain.repository;

/** Domain-facing contract; the Firebase/HTTP implementation lives in data/. */
public interface ExampleRepository {

    /** userId first: every call is scoped to one signed-in user. */
    void doThing(String userId, String value, ResultCallback<ResultType> callback);
}
```

## Fake (`app/src/test/java/com/portfolioiq/domain/repository/FakeExampleRepository.java`)

```java
package com.portfolioiq.domain.repository;

/** Hand-written test double (no mocking framework). */
public class FakeExampleRepository implements ExampleRepository {

    public boolean doThingCalled = false;
    public String lastUserId;
    public String lastValue;

    public ResultType resultToReturn;
    public String errorToReturn = null;

    @Override
    public void doThing(String userId, String value, ResultCallback<ResultType> callback) {
        doThingCalled = true;
        lastUserId = userId;
        lastValue = value;
        if (errorToReturn != null) callback.onError(errorToReturn);
        else callback.onSuccess(resultToReturn);
    }
}
```

## Test (`app/src/test/java/com/portfolioiq/domain/usecase/ExampleUseCaseTest.java`)

```java
package com.portfolioiq.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.portfolioiq.domain.repository.FakeExampleRepository;
import com.portfolioiq.domain.repository.ResultCallback;

import org.junit.Before;
import org.junit.Test;

public class ExampleUseCaseTest {

    private FakeExampleRepository fakeRepository;
    private ExampleUseCase useCase;

    @Before
    public void setUp() {
        fakeRepository = new FakeExampleRepository();
        useCase = new ExampleUseCase(fakeRepository);
    }

    @Test
    public void blankInput_isRejectedWithoutCallingRepository() {
        Result r = execute("user-1", "  ");

        assertEquals("<exact message the user sees>", r.error);
        assertFalse(fakeRepository.doThingCalled);
    }

    @Test
    public void validInput_isTrimmedAndDelegated() {
        Result r = execute("user-1", "  value  ");

        assertTrue(fakeRepository.doThingCalled);
        assertEquals("user-1", fakeRepository.lastUserId);
        assertEquals("value", fakeRepository.lastValue);
    }

    @Test
    public void repositoryError_isPassedThrough() {
        fakeRepository.errorToReturn = "Could not reach the server. Please try again.";

        Result r = execute("user-1", "value");

        assertEquals("Could not reach the server. Please try again.", r.error);
    }

    private Result execute(String userId, String input) {
        Result r = new Result();
        useCase.execute(userId, input, new ResultCallback<ResultType>() {
            @Override public void onSuccess(ResultType value) { r.value = value; }
            @Override public void onError(String message) { r.error = message; }
        });
        return r;
    }

    private static class Result {
        ResultType value;
        String error;
    }
}
```
