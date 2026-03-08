# Raoh Spring Example

A Spring Boot application demonstrating Raoh at both the **HTTP boundary** (JSON decoding)
and the **JDBC boundary** (row decoding with `MapDecoders`).

## What it shows

| Raoh feature | Where it appears |
| --- | --- |
| `JsonDecoders.combine` + `field` | Request body decoding in `MembershipDecoders` |
| `MapDecoders.combine` + `field` | JDBC row decoding (`USER_ROW`, `GROUP_ROW`, etc.) |
| `Decoder#list()` | Decoding a variable-length list of rows from `JdbcClient` |
| `Result.map2` | Combining user + group-memberships from two queries |
| `Decoders.withDefault` | Optional fields with defaults (`description`, `role`) |
| `StringDecoder` chain | `.trim().nonBlank().maxLength()`, `.toLowerCase().email()` |

## Domain model

```text
User ──< Membership >── Group
```

- **User** — `id`, `name`, `email`
- **Group** — `id`, `name`, `description`
- **Membership** — `user_id`, `group_id`, `role` (ADMIN / MEMBER)

## How to run

From the repository root, install the Raoh jars locally:

```bash
mvn install -DskipTests
```

Then start the example:

```bash
cd examples/spring
mvn spring-boot:run
```

## REST API

### Users

| Method | Path | Description |
| --- | --- | --- |
| `POST /users` | Create a user | Body: `{ "name": "Alice", "email": "alice@example.com" }` |
| `GET /users` | List all users | |
| `GET /users/{id}` | Show user with groups | Uses `Result.map2` + `Decoder#list()` |

### Groups

| Method | Path | Description |
| --- | --- | --- |
| `POST /groups` | Create a group | Body: `{ "name": "Engineering", "description": "..." }` |
| `GET /groups` | List all groups | |
| `GET /groups/{id}` | Show a group | |

### Memberships

| Method | Path | Description |
| --- | --- | --- |
| `POST /groups/{id}/members` | Add a member | Body: `{ "userId": 1, "role": "ADMIN" }` (role defaults to MEMBER) |
| `GET /groups/{id}/members` | List group members | |
| `DELETE /groups/{id}/members/{userId}` | Remove a member | |

## Quick Manual Check

Start the app, then use the following commands.

### 1. Create a user

```bash
curl -s -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"name": "Alice", "email": "Alice@Example.COM"}' | jq .
```

Expected: `201 Created` with normalized email `alice@example.com`.

### 2. Validation errors

```bash
curl -s -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"name": "", "email": "bad"}' | jq .
```

Expected: `400 Bad Request` with issues for `/name` and `/email`.

### 3. Create a group and add a member

```bash
curl -s -X POST http://localhost:8080/groups \
  -H 'Content-Type: application/json' \
  -d '{"name": "Engineering"}' | jq .

curl -s -X POST http://localhost:8080/groups/1/members \
  -H 'Content-Type: application/json' \
  -d '{"userId": 1, "role": "ADMIN"}' | jq .
```

### 4. Show user with groups

```bash
curl -s http://localhost:8080/users/1 | jq .
```

Expected: user record with a `groups` array showing the membership.

## Example error response

```json
{
  "issues": [
    { "path": "/name", "code": "blank", "message": "must not be blank", "meta": {} },
    { "path": "/email", "code": "invalid_format", "message": "invalid format", "meta": {} }
  ],
  "errors": {
    "/name": ["must not be blank"],
    "/email": ["invalid format"]
  }
}
```
