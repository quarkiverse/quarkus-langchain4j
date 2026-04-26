# Tool Guardrails Demo

This demo showcases the implementation of **guardrails** in Quarkus LangChain4j, including tool/function calling. 
Guardrails provide a mechanism to validate and control tool invocations at both input and output stages.

## What are Guardrails?

Guardrails are validation and transformation mechanisms that:

- **Input Guardrails**: Validate parameters and context before execution
- **Output Guardrails**: Validate, filter, or transform results before returning to the LLM

This enables:
- Security controls (authorization, authentication)
- Data validation (format checking, business rules)
- Privacy protection (sensitive data filtering)
- Cost control (rate limiting, output size limiting)
- Compliance requirements

## Demo Overview

This demo includes 7 different guardrails demonstrating various use cases:

### Input Guardrails

1. **EmailFormatValidator** - Validates email address format
   - Checks against regex pattern
   - Prevents execution if email is invalid
   - Returns helpful error message to LLM

2. **UserAuthorizationGuardrail** - Checks user permissions
   - Uses CDI injection (RequestScoped)
   - Integrates with Quarkus Security
   - Role-based access control

3. **RateLimitGuardrail** - Prevents abuse through rate limiting
   - Stateful (maintains counters)
   - Per-user/session limits
   - Time-window based (5 calls per minute)

### Output Guardrails

4. **SensitiveDataFilter** - Redacts sensitive information
   - Filters SSN, credit card numbers
   - Regex-based pattern matching
   - Transforms output before returning to LLM

5. **OutputSizeLimiter** - Controls output size
   - Truncates large results
   - Prevents excessive token usage
   - Adds truncation notice

6. **Combined Guardrails** - Multiple guardrails on one tool
   - Demonstrates guardrail chaining
   - Ordered execution
    
7. **Profanity Guardrail** - Validation of AI output.
   - The simplest use case to show the basics
   - Checks chat AI output, fails if it contains prohibited word

## Running the Demo

### Start the application in dev mode:

```bash
mvn quarkus:dev -Dquarkus.langchain4j.openai.api-key=$API_KEY
```

The application will start on http://localhost:8080

### Example Requests

#### 1. Send Email (with input validation)

```bash
# Valid email - should succeed
curl "http://localhost:8080/email?request=Send%20an%20email%20to%20john@quarkus.io%20with%20subject%20'Hello'%20and%20body%20'How%20are%20you?'"

# Invalid email - guardrail will reject
curl "http://localhost:8080/email?request=Send%20an%20email%20to%20invalid-email%20with%20subject%20'Test'"
```

#### 2. Get Customer Info (with output filtering)

```bash
# SSN and credit card numbers will be redacted
curl "http://localhost:8080/email?request=Get%20customer%20information%20for%20customer%20ID%2012345"
```

#### 3. Bulk Email (with rate limiting)

```bash
# Send multiple requests quickly to trigger rate limit
for i in {1..10}; do
  curl "http://localhost:8080/email?request=Send%20bulk%20email%20to%20alice@example.com,bob@example.com%20with%20subject%20'Test%20$i'"
  sleep 1
done
```

#### 4. Search Customers (with output size limiting)

```bash
# Large result set will be truncated
curl "http://localhost:8080/email?request=Search%20for%20all%20customers"
```


#### 4. Chat with the assistant (with output moderation)

```bash
$ curl -X POST localhost:8080/chatbot/moderated -w "\n" -d 'Hello, my name is Meatbag'
[The AI answered with expletive]
# The similar but non-prohibited name will not be flagged 
$ curl -X POST localhost:8080/chatbot/moderated -w "\n" -d 'Hello, my name is Fleabag'
Hello Fleabag! How can I assist you today?
```
