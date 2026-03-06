# Raoh Spring Example

This is a standalone Spring Boot application that uses Raoh to decode request JSON inside a `RestController`.

## What it shows

- accepting raw `JsonNode` in a controller
- decoding request bodies with `JsonDecoder<T>`
- returning typed values on success
- returning structured `Issues` on failure
- combining field-level decoding and cross-field domain rules

## How to run

Because this is a standalone Maven project, it points to the locally built Raoh jar under `../../target`.

From the repository root:

```bash
mvn package
```

Then run the example:

```bash
cd examples/spring
mvn spring-boot:run
```

## Quick Manual Check

Start the app first:

```bash
cd examples/spring
mvn spring-boot:run
```

Then use the following commands in another terminal.

### 1. Valid request

```bash
curl -i \
  -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "Alice@Example.com",
    "age": 24,
    "address": {
      "city": "Tokyo",
      "postalCode": "123-4567"
    },
    "tags": ["alpha", "beta"]
  }'
```

Expected:

- HTTP `201 Created`
- normalized email (`alice@example.com`)
- default role (`MEMBER`)

### 2. Invalid email and invalid nested fields

```bash
curl -i \
  -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "bad",
    "age": 24,
    "address": {
      "city": "",
      "postalCode": "1234"
    }
  }'
```

Expected:

- HTTP `400 Bad Request`
- issues for `/email`, `/address/city`, and `/address/postalCode`

### 3. Cross-field domain rule failure

```bash
curl -i \
  -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "admin@example.com",
    "age": 17,
    "role": "admin",
    "address": {
      "city": "Tokyo",
      "postalCode": "123-4567"
    }
  }'
```

Expected:

- HTTP `400 Bad Request`
- issue for `/role`
- message saying admin users must be at least 20 years old

### 4. Tags fallback

The controller uses `withDefault(...)` for `tags`, so omitting the field is allowed.

```bash
curl -i \
  -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "no-tags@example.com",
    "age": 30,
    "address": {
      "city": "Osaka",
      "postalCode": "987-6543"
    }
  }'
```

Expected:

- HTTP `201 Created`
- `"tags": []`

### 5. Too many tags

```bash
curl -i \
  -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "tags@example.com",
    "age": 30,
    "address": {
      "city": "Nagoya",
      "postalCode": "111-2222"
    },
    "tags": ["a", "b", "c", "d", "e", "f"]
  }'
```

Expected:

- HTTP `400 Bad Request`
- issue for `/tags`

## Endpoint

`POST /users`

Example request:

```json
{
  "email": "Alice@Example.com",
  "age": 24,
  "role": "member",
  "address": {
    "city": "Tokyo",
    "postalCode": "123-4567"
  },
  "tags": ["alpha", "beta"]
}
```

Example success response:

```json
{
  "id": "generated-user-id",
  "email": "alice@example.com",
  "age": 24,
  "role": "MEMBER",
  "address": {
    "city": "Tokyo",
    "postalCode": "123-4567"
  },
  "tags": ["alpha", "beta"]
}
```

Example error response:

```json
{
  "issues": [
    {
      "path": "/email",
      "code": "invalid_format",
      "message": "invalid format",
      "meta": {}
    }
  ],
  "errors": {
    "/email": ["invalid format"]
  }
}
```
