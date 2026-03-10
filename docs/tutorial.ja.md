# Raohデコーダー実践ガイド

このガイドは jetshell を使ってRaohのデコーダーをインタラクティブに学ぶためのチュートリアルです。基礎から始めて、実業務で頻出するパターンまで順を追って説明します。

---

## なぜデコーダーなのか？

Webアプリケーションやバッチ処理では、外部からやってくるデータを信頼することはできません。HTTPリクエストのボディ、CSVファイル、外部APIのレスポンス、データベースのカラム値——これらはどれも、アプリケーションが期待する型や制約を満たしている保証がありません。

典型的な対処として、各フィールドを個別にバリデーションするコードを書くことが多いでしょう。しかし、この方法にはいくつかの問題があります。

- **エラーが途中で止まる。** `if` 文の連鎖では、最初に見つかったエラーで処理が打ち切られ、「メールアドレスも年齢も不正」という場合に一方しか報告できません。
- **変換とバリデーションが分離しにくい。** 「文字列を受け取って検証し、ドメイン型に変換する」という一連の作業が、チェック処理とキャスト処理に分散しがちです。
- **エラーの構造が失われる。** どのフィールドのどんな問題かを呼び出し元に伝えるために、独自の例外クラスや戻り値の規約を設けることになります。

**Raohのデコーダーは、これら3つの問題を同時に解決します。**

デコーダーは「入力値を受け取り、`Ok[値]` か `Err[エラー群]` を返す関数」です。`combine` を使って複数のデコーダーを合成すると、全フィールドのエラーが一度に蓄積されます。エラーにはフィールドへのパス（`/address/zip` のような）が自動的に付与されます。そしてデコードが成功した時点で、値はすでにドメイン型に変換済みです。

```text
外部入力 (Object / Map / JsonNode / JooqRecord)
    ↓  decoder.decode(input)
Ok[ドメインオブジェクト]  または  Err[{path: "/email", code: "invalid_format", ...}, ...]
```

境界でデコードを通過した値は、アプリケーションの内側では「すでに正しい」ものとして扱えます。バリデーション漏れを防ぎ、型によってドメインルールが保証されます。

このガイドでは、プリミティブ値の検証から始めて、ネストしたオブジェクト、リスト、クロスフィールドバリデーション、実業務のシナリオまでを段階的に学びます。

---

## 0. 準備

[jetshell](https://github.com/kawasima/jetshell) を起動してRaohを読み込みます。

```bash
jetshell
/resolve net.unit8.raoh:raoh:0.4.0
import static net.unit8.raoh.ObjectDecoders.*;
import static net.unit8.raoh.map.MapDecoders.*;
import static net.unit8.raoh.Decoders.*;
import net.unit8.raoh.*;
import net.unit8.raoh.map.*;
import java.util.stream.*;
import java.time.*;
```

以降のサンプルでは、上記のimportが完了していることを前提としています。`/resolve` コマンドでMavenリポジトリから直接取得することもできます（jetshellのバージョンによって対応が異なります）。

---

## 1. プリミティブ値のデコード

Raohの最小単位は、単一の値を読み取るデコーダーです。`ObjectDecoders` の `string()`, `int_()`, `decimal()`, `bool()` がそれぞれの型に対応しています。

```java
string().decode("hello")
// ==> Ok[hello]

int_().decode(42)
// ==> Ok[42]

bool().decode(true)
// ==> Ok[true]

decimal().decode(19.99)
// ==> Ok[19.99]
```

各メソッドは `Decoder<Object, T>` を返します。期待する型でなければ `type_mismatch` エラーになります。

```java
int_().decode("not a number")
// ==> Err[/: expected integer]
```

`JsonDecoders`, `MapDecoders`, `JooqRecordDecoders` などの境界モジュールは、すべてこのプリミティブデコーダーの上に構築されています。

---

## 2. 制約の付与

デコーダーにはチェーンで制約を付けられます。制約に違反するとデコード失敗になります。

### 文字列の制約

```java
string().trim().toLowerCase().email().decode("  USER@Example.COM  ")
// ==> Ok[user@example.com]

string().trim().toLowerCase().email().decode("not-email")
// ==> Err[/: not a valid email]

string().minLength(3).maxLength(20).decode("ab")
// ==> Err[/: must be at least 3 characters]

string().pattern(Pattern.compile("^\\d{3}-\\d{4}$")).decode("123-4567")
// ==> Ok[123-4567]

string().uuid().decode("550e8400-e29b-41d4-a716-446655440000")
// ==> Ok[550e8400-e29b-41d4-a716-446655440000]
```

### 数値の制約

```java
int_().range(0, 150).decode(25)
// ==> Ok[25]

int_().range(0, 150).decode(300)
// ==> Err[/: must be between 0 and 150]

int_().positive().decode(-1)
// ==> Err[/: must be positive]

decimal().scale(4).decode(new BigDecimal("0.1234"))
// ==> Ok[0.1234]
```

### 日時の制約

```java
string().date().decode("2025-06-15")
// ==> Ok[2025-06-15]

string().iso8601().decode("2025-06-15T10:30:00Z")
// ==> Ok[2025-06-15T10:30:00Z]
```

### 文字列からの型変換（coerce）

フォームデータやクエリパラメータでは、数値や真偽値もすべて文字列として届きます。`toInt()`, `toLong()`, `toDecimal()`, `toBool()` を使うと、文字列をパースして型付きデコーダーに変換できます。変換後はそのまま制約をチェーンできます。

```java
string().toInt().decode("42")
// ==> Ok[42]

string().toInt().range(0, 150).decode("25")
// ==> Ok[25]

string().toInt().decode("abc")
// ==> Err[/: expected integer]

string().toLong().decode("9999999999")
// ==> Ok[9999999999]

string().toDecimal().scale(2).decode("19.99")
// ==> Ok[19.99]

string().toDecimal().decode("abc")
// ==> Err[/: expected decimal]
```

`toBool()` はフォームデータでよく使われる文字列をケース非依存で認識します。

```java
string().toBool().decode("true")
// ==> Ok[true]

string().toBool().decode("1")
// ==> Ok[true]

string().toBool().decode("no")
// ==> Ok[false]

string().toBool().decode("maybe")
// ==> Err[/: expected boolean]

// isTrue() で「利用規約への同意」を強制
string().toBool().isTrue().decode("true")
// ==> Ok[true]

string().toBool().isTrue().decode("false")
// ==> Err[/: must be true]
```

`toBool()` が認識する値: `true`/`1`/`yes`/`on` → `true`、`false`/`0`/`no`/`off` → `false`。

前段で `trim()` を挟むと、ホワイトスペース付きの入力にも対応できます。

```java
string().trim().toInt().decode("  42  ")
// ==> Ok[42]
```

---

## 3. ドメインプリミティブへの変換

`map` を使うと、生の値をドメイン固有の型に変換できます。これがRaohの核心です。境界でドメイン型に昇格させることで、アプリケーションの内側では「すでに検証済み・変換済みの値」だけが流通します。

```java
record Email(String value) {}
record Age(int value) {}
record UserId(UUID value) {}

var emailDec = string().trim().toLowerCase().email().map(Email::new);
var ageDec = int_().range(0, 150).map(Age::new);
var userIdDec = string().uuid().map(UserId::new);

emailDec.decode("  ALICE@example.com  ")
// ==> Ok[Email[value=alice@example.com]]

ageDec.decode(25)
// ==> Ok[Age[value=25]]

userIdDec.decode("550e8400-e29b-41d4-a716-446655440000")
// ==> Ok[UserId[value=550e8400-e29b-41d4-a716-446655440000]]
```

これらのデコーダーは境界モジュールの `field()` にそのまま渡せます。`map` はデコード成功時のみ実行されます。失敗時はそのまま `Err` が返ります。

---

## 4. オブジェクトのデコード — combine + field

ここから `MapDecoders` に切り替えます。`Map<String, Object>` を入力として `field()` と `combine()` でオブジェクトを組み立てます。

```java
import static net.unit8.raoh.map.MapDecoders.*
```

`combine` はapplicative合成であり、各フィールドのエラーが独立に蓄積されます。フォームバリデーションで「全エラーを一度に返したい」という要件に自然にフィットします。

```java
record User(UserId id, Email email, Age age) {}

var userDec = combine(
        field("id", string().uuid().map(UserId::new)),
        field("email", string().trim().toLowerCase().email().map(Email::new)),
        field("age", int_().range(0, 150).map(Age::new))
).map(User::new);

userDec.decode(Map.of(
        "id", "550e8400-e29b-41d4-a716-446655440000",
        "email", "alice@example.com",
        "age", 30
))
// ==> Ok[User[id=UserId[...], email=Email[value=alice@example.com], age=Age[value=30]]]
```

不正な入力では、3フィールドすべてのエラーが一度に返ります。途中で打ち切られません。

```java
userDec.decode(Map.of("id", "not-uuid", "email", "invalid", "age", 300))
// ==> Err[/id: not a valid UUID, /email: not a valid email, /age: must be between 0 and 150]
```

---

## 5. ネストしたオブジェクト

`field` の中にオブジェクトデコーダーを渡すだけでネスト構造を表現できます。エラーパスは自動的に `/address/city` のように合成されます。

```java
record Address(String city, String zip) {}
record Customer(String name, Address address) {}

var addressDec = combine(
        field("city", string().nonBlank()),
        field("zip", string().pattern(Pattern.compile("^\\d{3}-\\d{4}$")))
).map(Address::new);

var customerDec = combine(
        field("name", string().nonBlank()),
        field("address", nested(addressDec))
).map(Customer::new);

customerDec.decode(Map.of(
        "name", "Alice",
        "address", Map.of("city", "Tokyo", "zip", "100-0001")
))
// ==> Ok[Customer[name=Alice, address=Address[city=Tokyo, zip=100-0001]]]

customerDec.decode(Map.of(
        "name", "",
        "address", Map.of("city", "", "zip", "bad")
))
// ==> Err[/name: is required, /address/city: is required, /address/zip: ...]
```

---

## 6. フラットデータの構造化 — nested

DBのJOIN結果やCSVでは、すべてのカラムが同一階層に並んだフラットな形式で届きます。`nested` を使うと、このフラットデータから構造化されたドメインモデルを組み立てられます。

セクション5の `field("address", nested(addressDec))` が入力にネスト構造を要求するのに対し、`nested` をトップレベルで使うと同じフラット入力の中から各デコーダーがそれぞれのフィールドを読み取ります。

```java
record UserName(String first, String last) {}
record Department(String name, String code) {}
record Employee(UserName name, Department dept) {}

var userNameDec = combine(
        field("first_name", string().nonBlank()),
        field("last_name", string().nonBlank())
).map(UserName::new);

var deptDec = combine(
        field("dept_name", string().nonBlank()),
        field("dept_code", string().nonBlank())
).map(Department::new);

// combine に直接渡すとフラット入力を共有してデコードする
var employeeDec = combine(userNameDec, deptDec).map(Employee::new);
```

JOINの結果をイメージしたフラットなMapを渡します。

```java
employeeDec.decode(Map.of(
        "first_name", "Alice",
        "last_name", "Smith",
        "dept_name", "Engineering",
        "dept_code", "ENG"
))
// ==> Ok[Employee[name=UserName[first=Alice, last=Smith], dept=Department[name=Engineering, code=ENG]]]
```

`combine(decA, decB)` では、両デコーダーが同じフラット入力を参照します。一方 `field("address", nested(decA))` は入力から `address` キーの値を取り出してから `decA` に渡します。

フィールド名が衝突する場合（両テーブルに `name` がある等）は、SQLのエイリアスで区別してからデコードします。

```java
// SELECT u.name AS user_name, d.name AS dept_name FROM ...
var userDec2 = field("user_name", string().nonBlank());
var deptDec2 = field("dept_name", string().nonBlank());

combine(userDec2, deptDec2)
        .map((userName, deptName) -> Map.of("user", userName, "dept", deptName))
        .decode(Map.of("user_name", "Alice", "dept_name", "Engineering"))
// ==> Ok[{user=Alice, dept=Engineering}]
```

LEFT JOINで結合先が存在しない場合、JOINカラムがすべて `null` になります。`optionalNullableField` と `Presence` で「行がJOINされなかった」を型安全に表現できます。

```java
record EmployeeWithDept(UserName name, Presence<Department> dept) {}

var deptPresenceDec = optionalNullableField("dept_name", string().nonBlank());

var row = new HashMap<String, Object>();
row.put("first_name", "Bob");
row.put("last_name", "Jones");
row.put("dept_name", null);
row.put("dept_code", null);

deptPresenceDec.decode(row)
// ==> Ok[PresentNull[]]  — JOINされなかったことを示す
```

### 1対多 JOIN — フラット行から親子モデルへの組み上げ

DBの1対多JOINクエリは、親カラムが重複したフラット行のリストを返します。これを `Order { lines: [OrderLine] }` のような親子ドメインモデルに組み上げるには、`nested` で各行をデコードした後、親キーでグルーピングします。

```java
record OrderHeader(String orderId, String customerName) {}
record OrderLine(String productId, int quantity, BigDecimal unitPrice) {}
record OrderWithLines(OrderHeader header, List<OrderLine> lines) {}
// 親デコーダー（ordersテーブルのカラム）
var headerDec = combine(
        field("order_id", string().nonBlank()),
        field("customer_name", string().nonBlank())
).map(OrderHeader::new);
// 子デコーダー（order_linesテーブルのカラム）
var lineDec = combine(
        field("product_id", string().nonBlank()),
        field("qty", int_().positive()),
        field("unit_price", decimal().positive())
).map(OrderLine::new);
// 1行ごとに親と子を同時にデコード
var rowDec = combine(
        nested(headerDec),
        nested(lineDec)
).map((h, l) -> Map.entry(h, l));
```

JOINクエリの結果をイメージしたフラット行です。`order_id` と `customer_name` が行ごとに重複しています。

```java
// SELECT o.order_id, o.customer_name, l.product_id, l.qty, l.unit_price
// FROM orders o JOIN order_lines l ON o.order_id = l.order_id
var joinRows = List.<Map<String, Object>>of(
        Map.of("order_id", "ORD-001", "customer_name", "Alice", "product_id", "A01", "qty", 2, "unit_price", 1500),
        Map.of("order_id", "ORD-001", "customer_name", "Alice", "product_id", "B02", "qty", 1, "unit_price", 3000),
        Map.of("order_id", "ORD-002", "customer_name", "Bob",   "product_id", "A01", "qty", 5, "unit_price", 1500)
);
```

各行をデコードし、親キーでグルーピングして親子モデルを組み立てます。

```java
// 全行をデコード
var decodedRows = Result.traverse(joinRows, rowDec::decode, Path.of("rows"));
// 親キーでグルーピング → OrderWithLines に組み上げ
var orders = decodedRows.map(entries ->
        entries.stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ))
                .entrySet().stream()
                .map(e -> new OrderWithLines(e.getKey(), e.getValue()))
                .toList()
);
orders
// ==> Ok[[OrderWithLines[header=OrderHeader[orderId=ORD-001, ...], lines=[OrderLine[...A01...], OrderLine[...B02...]]], OrderWithLines[header=OrderHeader[orderId=ORD-002, ...], lines=[OrderLine[...A01...]]]]]
```

ポイント:

- `nested` でフラット行から親・子を同時デコード
- `Result.traverse` で全行のエラーを蓄積
- `Result.map` + `Collectors.groupingBy` で親キー単位にグルーピング
- デコード（検証）とグルーピング（構造変換）が明確に分離される

---

## 7. リストのデコード

リスト内の全要素が検査され、どの要素で失敗したかがインデックス付きのパスで報告されます。

```java
record OrderItem(String productId, int quantity) {}

var orderItemDec = combine(
        field("productId", string().nonBlank()),
        field("quantity", int_().positive())
).map(OrderItem::new);

var orderItemsDec = field("items", list(nested(orderItemDec)).nonempty());

orderItemsDec.decode(Map.of("items", List.of(
        Map.of("productId", "A001", "quantity", 3),
        Map.of("productId", "", "quantity", -1)
)))
// ==> Err[/items/1/productId: is required, /items/1/quantity: must be positive]
```

---

## 8. マップのデコード

キーが動的な辞書構造には `map(decoder)` を使います。

```java
var pricesDec = field("prices", map(decimal().positive()).minSize(1));

pricesDec.decode(Map.of("prices", Map.of("apple", 120, "banana", 80)))
// ==> Ok[{apple=120, banana=80}]
```

---

## 9. Optional / Nullable / 三値フィールド

「フィールドが存在しない」「`null` が明示的に送られた」「値がある」の3状態を型レベルで分離できます。

```java
// フィールド必須、nullは不可
field("email", string().email()).decode(Map.of("email", "a@b.com"))
// ==> Ok[a@b.com]

// フィールド必須、nullは許可
field("nickname", nullable(string())).decode(Map.of("nickname", "alice"))
// ==> Ok[alice]

// フィールド自体が省略可能
optionalField("middleName", string()).decode(Map.of())
// ==> Ok[Optional.empty]
```

三値は `optionalNullableField` で扱います。戻り値の `Presence<T>` が3状態を区別します。

```java
optionalNullableField("bio", string()).decode(Map.of("bio", "hello"))
// ==> Ok[Present[value=hello]]

var m = new HashMap<String, Object>(); m.put("bio", null);
optionalNullableField("bio", string()).decode(m)
// ==> Ok[PresentNull[]]

optionalNullableField("bio", string()).decode(Map.of())
// ==> Ok[Absent[]]
```

- `Presence.Present(value)` — 値がセットされている
- `Presence.PresentNull` — 明示的に `null` が送られた
- `Presence.Absent` — フィールド自体が存在しない

PATCH APIで「値をクリアする」と「変更しない」を区別する場面で有用です。

```java
record ProfilePatch(Presence<String> nickname, Presence<String> bio) {}

var profilePatchDec = combine(
        optionalNullableField("nickname", string().maxLength(50)),
        optionalNullableField("bio", string().maxLength(500))
).map(ProfilePatch::new);

profilePatchDec.decode(Map.of("nickname", "alice"))
// ==> Ok[ProfilePatch[nickname=Present[value=alice], bio=Absent[]]]
```

---

## 10. Enum値のデコード

JavaのEnumをケース非依存でデコードできます。

```java
enum Role { ADMIN, MEMBER, GUEST }

field("role", enumOf(Role.class)).decode(Map.of("role", "admin"))
// ==> Ok[ADMIN]

field("role", enumOf(Role.class)).decode(Map.of("role", "ADMIN"))
// ==> Ok[ADMIN]

field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of())
// ==> Ok[MEMBER]
```

---

## 11. Union型 — oneOf による判別

`kind` フィールドで型を判別し、それぞれ異なる構造をデコードするパターンです。

```java
sealed interface Contact {}
record EmailContact(String address) implements Contact {}
record PhoneContact(String number) implements Contact {}

var contactDec = oneOf(
        combine(
                field("kind", literal("email")),
                field("value", string().email())
        ).map((kind, value) -> (Contact) new EmailContact(value)),
        combine(
                field("kind", literal("phone")),
                field("value", string().pattern(Pattern.compile("^\\d{10,15}$")))
        ).map((kind, value) -> (Contact) new PhoneContact(value))
);

contactDec.decode(Map.of("kind", "email", "value", "a@b.com"))
// ==> Ok[EmailContact[address=a@b.com]]

contactDec.decode(Map.of("kind", "phone", "value", "09012345678"))
// ==> Ok[PhoneContact[number=09012345678]]

contactDec.decode(Map.of("kind", "fax", "value", "123"))
// ==> Err[/: no variant matched]
```

すべての候補がマッチしなかった場合、`no variant matched` エラーが返ります。

---

## 12. クロスフィールドバリデーション — flatMap

複数フィールド間の整合性チェックには `flatMap` を使います。`combine` でフィールドを読み取った後、ドメインルールを適用します。

```java
record DateRange(LocalDate start, LocalDate end) {
    static Result<DateRange> parse(LocalDate start, LocalDate end) {
        if (!start.isBefore(end)) {
            return Result.fail(Path.ROOT, "invalid_range", "start must be before end");
        }
        return Result.ok(new DateRange(start, end));
    }
}

var dateRangeDec = combine(
        field("start", string().date()),
        field("end", string().date())
).flatMap(DateRange::parse);

dateRangeDec.decode(Map.of("start", "2025-01-01", "end", "2025-12-31"))
// ==> Ok[DateRange[start=2025-01-01, end=2025-12-31]]

dateRangeDec.decode(Map.of("start", "2025-12-31", "end", "2025-01-01"))
// ==> Err[/: start must be before end]
```

`flatMap` 内で返した `Result.fail` のパスは呼び出し元のパスに自動でリベースされます。`field("period", dateRangeDec)` として使えばエラーパスは `/period` になります。

`flatMap` は前段が成功した場合だけ後段が実行されます。独立した2つの結果を合成する用途には `Result.map2` を使います（後述）。

---

## 13. 条件付きデコード — method フィールドによる分岐

あるフィールドの値によって後続のデコード方法が変わるケースです。`Decoder<I, T>` ラムダとして記述し、最初に `method` フィールドを読んでから残りのフィールドを切り替えます。

```java
interface Payment {}
record CreditCard(String number, String expiry) implements Payment {}
record BankTransfer(String bankCode, String accountNumber) implements Payment {}

Decoder<Map<String, Object>, Payment> paymentDec = (in, path) -> {
    var mr = field("method", string()).decode(in, path);
    if (mr instanceof Err<String> e) return Result.err(e.issues());
    return switch (((Ok<String>) mr).value()) {
        case "credit_card" -> combine(
                field("number", string().nonBlank()),
                field("expiry", string().pattern(Pattern.compile("^\\d{2}/\\d{2}$")))
        ).map((n, e) -> (Payment) new CreditCard(n, e)).decode(in, path);
        case "bank_transfer" -> combine(
                field("bankCode", string().nonBlank()),
                field("accountNumber", string().nonBlank())
        ).map((b, a) -> (Payment) new BankTransfer(b, a)).decode(in, path);
        default -> Result.fail("unsupported_method", "Unknown payment method: " + ((Ok<String>) mr).value());
    };
};

paymentDec.decode(Map.of("method", "credit_card", "number", "4111111111111111", "expiry", "12/26"))
// ==> Ok[CreditCard[number=4111111111111111, expiry=12/26]]

paymentDec.decode(Map.of("method", "bank_transfer", "bankCode", "0001", "accountNumber", "1234567"))
// ==> Ok[BankTransfer[bankCode=0001, accountNumber=1234567]]
```

---

## 14. 異なるソースの合成 — Result.map2

`combine` は同一入力からフィールドを取り出す合成です。異なるデータソース（別テーブル、別API応答など）から得た2つの `Result` を合成するには `Result.map2` を使います。

```java
record PersonalName(String first, String last) {}
record ContactInfo(String email, String phone) {}
record CustomerProfile(PersonalName name, ContactInfo contact) {}

var nameDec = combine(
        field("first", string().nonBlank()),
        field("last", string().nonBlank())
).map(PersonalName::new);

var contactInfoDec = combine(
        field("email", string().email()),
        field("phone", string().nonBlank())
).map(ContactInfo::new);

var nameResult = nameDec.decode(Map.of("first", "Alice", "last", "Smith"));
var contactResult = contactInfoDec.decode(Map.of("email", "a@b.com", "phone", "090-1234-5678"));

Result.map2(nameResult, contactResult, CustomerProfile::new)
// ==> Ok[CustomerProfile[name=PersonalName[...], contact=ContactInfo[...]]]
```

両方失敗しても双方のエラーがマージされます。`combine` は「デコード前」の合成、`Result.map2` は「デコード後」の合成です。

---

## 15. リスト要素の一括デコード — Result.traverse

複数行をドメインオブジェクトのリストに変換する場合、`Result.traverse` がすべての行を検査しエラーを蓄積します。

```java
record Order(String orderId, BigDecimal total) {}

var orderDec = combine(
        field("order_id", string().nonBlank()),
        field("total", decimal().nonNegative())
).map(Order::new);

var rows = List.<Map<String, Object>>of(
        Map.of("order_id", "A001", "total", 1000),
        Map.of("order_id", "", "total", -500),
        Map.of("order_id", "A003", "total", 300)
);

Result.traverse(rows, orderDec::decode, Path.of("orders"))
// ==> Err[/orders/1/order_id: is required, /orders/1/total: must be non-negative]
```

インデックス付きのパス（`/orders/1/order_id` など）でどの行が失敗したかわかります。

---

## 16. デフォルト値とリカバリ

### withDefault — 欠損時のフォールバック

フィールドが存在しないか `null` のときだけデフォルト値を適用します。値があって不正な場合はエラーになります。

```java
field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of())
// ==> Ok[MEMBER]

field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of("role", "admin"))
// ==> Ok[ADMIN]

field("role", withDefault(enumOf(Role.class), Role.MEMBER)).decode(Map.of("role", "invalid"))
// ==> Err[/role: ...]
```

### recover — あらゆる失敗からのフォールバック

値が不正な場合も含め、あらゆるデコード失敗をフォールバック値で吸収します。

```java
recover(field("pageSize", int_().range(1, 100)), 20).decode(Map.of("pageSize", 999))
// ==> Ok[20]

recover(field("pageSize", int_().range(1, 100)), 20).decode(Map.of())
// ==> Ok[20]
```

`withDefault` は「概念的にオプショナル」な場面、`recover` は「どんな入力が来ても壊れてほしくない」場面で使い分けます。

---

## 17. strictモード — 未知フィールドの拒否

スキーマに定義されていないフィールドが混入していたら拒否します。

```java
record ApiRequest(String action, int amount) {}

var apiRequestDec = combine(
        field("action", string().nonBlank()),
        field("amount", int_().positive())
).strict(ApiRequest::new);

apiRequestDec.decode(Map.of("action", "transfer", "amount", 100))
// ==> Ok[ApiRequest[action=transfer, amount=100]]

apiRequestDec.decode(Map.of("action", "transfer", "amount", 100, "extra", true))
// ==> Err[/extra: unknown_field]
```

---

## 18. 再帰構造 — lazy

型が自分自身を参照する場合は `lazy` で循環参照を解決します。

```java
record Comment(String body, List<Comment> replies) {}

Decoder[] self = new Decoder[1];
self[0] = combine(
        field("body", string().nonBlank()),
        withDefault(field("replies", list(lazy(() -> self[0]))), List.of())
).map(Comment::new);
var commentDec = self[0];

commentDec.decode(Map.of(
        "body", "top",
        "replies", List.of(
                Map.of("body", "reply1", "replies", List.of()),
                Map.of("body", "reply2", "replies", List.of(
                        Map.of("body", "nested", "replies", List.of())
                ))
        )
))
// ==> Ok[Comment[body=top, replies=[Comment[body=reply1, ...], Comment[body=reply2, ...]]]]
```

---

## 19. エラーハンドリングの実践パターン

Raohのエラーは構造化されており、用途に応じて複数の表現形式で取り出せます。

```java
var checkDec = combine(
        field("email", string().email()),
        field("age", int_().range(0, 150))
).map((email, age) -> Map.of("email", email, "age", age));

var result = checkDec.decode(Map.of("email", "bad", "age", 300));
```

### フォーム向け — flatten

パスをキー、メッセージのリストを値とするフラットなマップです。フロントのフォームバリデーション表示に直結します。

```java
switch (result) {
    case Err(var issues) -> issues.flatten();
    default -> {}
}
// ==> {/email=[not a valid email], /age=[must be between 0 and 150]}
```

### API向け — toJsonList

各エラーを `path`, `code`, `message`, `meta` を含むオブジェクトのリストとして返します。REST APIのエラーレスポンスに直結します。

```java
switch (result) {
    case Err(var issues) -> issues.toJsonList();
    default -> {}
}
// ==> [{path=/email, code=invalid_format, message=not a valid email, meta={}}, ...]
```

### ロケール対応メッセージ

`ResourceBundleMessageResolver` と組み合わせると、デコーダーを変更することなくロケールに応じたメッセージを生成できます。

```java
var resolver = new ResourceBundleMessageResolver("messages");
switch (result) {
    case Err(var issues) -> issues.resolve(resolver, Locale.JAPANESE);
    default -> {}
}
```

---

## 20. 業務シナリオ: ユーザー登録API

ここまでのパターンを組み合わせた実例です。

```java
sealed interface ContactMethod {}
record EmailMethod(String address) implements ContactMethod {}
record PhoneMethod(String number) implements ContactMethod {}
record Password(String value) {}
record UserRegistration(Email email, Password password, Role role, List<ContactMethod> contacts) {}

var contactMethodDec = oneOf(
        combine(
                field("kind", literal("email")),
                field("value", string().email())
        ).map((k, v) -> (ContactMethod) new EmailMethod(v)),
        combine(
                field("kind", literal("phone")),
                field("value", string().pattern(Pattern.compile("^\\d{10,15}$")))
        ).map((k, v) -> (ContactMethod) new PhoneMethod(v))
);

var userRegDec = combine(
        field("email", string().trim().toLowerCase().email().map(Email::new)),
        field("password", string().minLength(8).maxLength(128).map(Password::new)),
        field("role", withDefault(enumOf(Role.class), Role.MEMBER)),
        field("contacts", list(contactMethodDec).nonempty())
).map(UserRegistration::new);

userRegDec.decode(Map.of(
        "email", "  ALICE@Example.com  ",
        "password", "secureP@ss1",
        "contacts", List.of(
                Map.of("kind", "email", "value", "alice@example.com"),
                Map.of("kind", "phone", "value", "09012345678")
        )
))
// ==> Ok[UserRegistration[email=Email[value=alice@example.com], password=Password[...], role=MEMBER, contacts=[EmailMethod[...], PhoneMethod[...]]]]
```

不正な入力で全エラーが蓄積されることを確認します。

```java
userRegDec.decode(Map.of(
        "email", "bad",
        "password", "short",
        "contacts", List.of(
                Map.of("kind", "email", "value", "not-email")
        )
))
// ==> Err[/email: not a valid email, /password: must be at least 8 characters, /contacts/0/value: not a valid email]
```

Spring MVC Controller でJSON入力を受け取る場合は `JsonDecoders` に差し替えます（参考コード）。

```java
// import static net.unit8.raoh.json.JsonDecoders.*;
//
// @PostMapping("/users")
// ResponseEntity<?> register(@RequestBody JsonNode body) {
//     return switch (userRegDec.decode(body)) {
//         case Ok(var reg) -> ResponseEntity.status(201).body(userService.register(reg));
//         case Err(var issues) -> ResponseEntity.badRequest().body(issues.toJsonList());
//     };
// }
```

---

## 21. リストをMapに変換する

**シーン:** 商品マスタAPIが `[{"id": "APPLE", "price": 120}, ...]` のようなリスト形式でデータを返す場合、アプリ内部では `Map<String, BigDecimal>` として持ちたいことがあります。ID→価格のルックアップを繰り返すなら、リストのままでは毎回線形探索になるため、デコード時点でMapに変換してしまうのが自然です。

`list()` でリストをデコードした後、`map` で `Collectors.toMap` を適用します。

```java
record Product(String id, BigDecimal price) {}

var productDec = combine(
        field("id", string().nonBlank()),
        field("price", decimal().positive())
).map(Product::new);

// リストをデコードしてからMapに変換
var priceMapDec = field("products",
        list(nested(productDec))
                .map(products -> products.stream()
                        .collect(Collectors.toMap(Product::id, Product::price))));

priceMapDec.decode(Map.of("products", List.of(
        Map.of("id", "APPLE",  "price", new BigDecimal("120")),
        Map.of("id", "BANANA", "price", new BigDecimal("80"))
)))
// ==> Ok[{APPLE=120, BANANA=80}]
```

IDの重複はドメインルール違反です。`flatMap` でチェックを追加できます。`Collectors.toMap` は重複キーで例外を投げますが、Raohのエラーとして返すほうがユーザーフレンドリーです。

```java
var strictPriceMapDec = field("products",
        list(nested(productDec))
                .flatMap(products -> {
                    var ids = products.stream().map(Product::id).toList();
                    var duplicates = ids.stream()
                            .filter(id -> ids.stream().filter(id::equals).count() > 1)
                            .distinct().toList();
                    if (!duplicates.isEmpty())
                        return Result.fail("duplicate_ids", "duplicate product ids: " + duplicates);
                    return Result.ok(products.stream()
                            .collect(Collectors.toMap(Product::id, Product::price)));
                }));

strictPriceMapDec.decode(Map.of("products", List.of(
        Map.of("id", "APPLE",  "price", new BigDecimal("120")),
        Map.of("id", "APPLE",  "price", new BigDecimal("99"))
)))
// ==> Err[/products: duplicate product ids: [APPLE]]
```

---

## 22. 既知フィールド＋残余フィールドのMap収集

**シーン:** 商品やイベントのような「基本属性＋自由な拡張属性」を持つエンティティを扱うとき、スキーマで決まった固定フィールドと動的な追加属性が同一のMapに混在することがあります。たとえば `{"name": "T-shirt", "color": "red", "size": "L"}` の `name` は必須フィールドですが、`color` や `size` はカテゴリによって変わる拡張属性です。

Jacksonの `@JsonAnySetter` に相当するパターンを、`Result.map2` で実現します。`strict` は未知フィールドを拒否しますが、このパターンでは未知フィールドを `Map` として**収集**します。

```java
record Item(String name, Map<String, String> attrs) {}

// 既知フィールドのセット
var knownFields = Set.of("name");

Decoder<Map<String, Object>, Item> itemDec = (in, path) -> {
    // 固定フィールドをデコード
    var nameDec = field("name", string().nonBlank());
    // 残余フィールドをすべて String として収集
    Decoder<Map<String, Object>, Map<String, String>> attrsDec = (m, p) -> {
        var attrs = m.entrySet().stream()
                .filter(e -> !knownFields.contains(e.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())));
        return Result.ok(attrs);
    };
    // map2 で両方を独立に実行し、エラーを蓄積しながら合成
    return Result.map2(
            nameDec.decode(in, path),
            attrsDec.decode(in, path),
            Item::new);
};

itemDec.decode(Map.of("name", "T-shirt", "color", "red", "size", "L"))
// ==> Ok[Item[name=T-shirt, attrs={color=red, size=L}]]
```

`Result.map2` を使うことで、固定フィールドのバリデーションエラーと残余収集が独立して実行されます。残余フィールドの値を `string()` でバリデーションしたい場合は `attrsDec` 内で各値を `string().decode(e.getValue(), ...)` に通します。

---

## 23. パスワード確認 — 典型的クロスフィールドバリデーション

**シーン:** パスワード変更フォームでは「新パスワードと確認パスワードが一致すること」「新パスワードが現在のパスワードと異なること」という2つのクロスフィールドルールがあります。これらはいずれも個別フィールドの制約ではなく、複数フィールドの値の組み合わせに対するルールです。

`combine` でフィールドを独立に検証した後、`flatMap` でクロスフィールドチェックを追加します。前段のバリデーションが全部通って初めて、`flatMap` 内のルールが実行されます。

```java
record PasswordChange(String current, String newPassword) {}

var passwordChangeDec = combine(
        field("currentPassword", string().nonBlank()),
        field("newPassword",     string().minLength(8).maxLength(128)),
        field("confirmPassword", string().nonBlank())
).flatMap((current, newPw, confirm) -> {
    if (!newPw.equals(confirm))
        return Result.fail("password_mismatch", "newPassword and confirmPassword do not match");
    if (current.equals(newPw))
        return Result.fail("same_password", "new password must differ from current password");
    return Result.ok(new PasswordChange(current, newPw));
});

passwordChangeDec.decode(Map.of(
        "currentPassword", "oldSecret",
        "newPassword",     "newSecret1",
        "confirmPassword", "newSecret1"
))
// ==> Ok[PasswordChange[current=oldSecret, newPassword=newSecret1]]

passwordChangeDec.decode(Map.of(
        "currentPassword", "oldSecret",
        "newPassword",     "short",
        "confirmPassword", "different"
))
// ==> Err[/newPassword: must be at least 8 characters]
// (flatMap は前段失敗で打ち切り。前段が全成功して初めて一致チェックが走る)
```

---

## 24. ページネーション / クエリパラメータ

**シーン:** 検索APIでは `page`, `size`, `sort` のようなクエリパラメータを受け取ります。これらには「省略された場合のデフォルト値」と「壊れた値への対処方針」の2軸があります。`page` は負数を明示的にエラーにしたい一方、`size` や `sort` は多少おかしな値でも安全なデフォルトにフォールバックして動作継続させたい、という違いが典型的です。

`withDefault`（欠損時のみフォールバック）と `recover`（あらゆる失敗をフォールバック）を使い分けることで、パラメータごとの耐障害性を明示的にコントロールできます。

```java
enum SortOrder { ASC, DESC }
record PageRequest(int page, int size, SortOrder order) {}

var pageRequestDec = combine(
        // page: 省略時は0だが、負数は明示的にエラー
        withDefault(field("page", int_().range(0, Integer.MAX_VALUE)), 0),
        // size: 省略時・壊れた値どちらも20にフォールバック
        recover(withDefault(field("size", int_().range(1, 100)), 20), 20),
        // order: 省略時・不正値どちらもASCにフォールバック
        recover(withDefault(field("sort", enumOf(SortOrder.class)), SortOrder.ASC), SortOrder.ASC)
).map(PageRequest::new);

// 通常リクエスト
pageRequestDec.decode(Map.of("page", 2, "size", 50, "sort", "desc"))
// ==> Ok[PageRequest[page=2, size=50, order=DESC]]

// 全パラメータ省略 — デフォルト値が使われる
pageRequestDec.decode(Map.of())
// ==> Ok[PageRequest[page=0, size=20, order=ASC]]

// size が範囲外 — recover でフォールバック
pageRequestDec.decode(Map.of("size", 9999))
// ==> Ok[PageRequest[page=0, size=20, order=ASC]]

// page の負数 — 仕様違反として明示的にエラー
pageRequestDec.decode(Map.of("page", -1))
// ==> Err[/page: must be between 0 and 2147483647]
```

`withDefault` は「このフィールドは省略可能だが、送られてきたら正しい値でなければならない」という意味論を表現します。`recover` は「どんな値が来ても安全に動作させたい」という意味論です。

---

## 25. 金額と通貨のクロスフィールドバリデーション

**シーン:** 決済システムや会計処理では、金額と通貨コードのペアを受け取る場面が頻繁にあります。金額の妥当性チェックは通貨ごとに異なります。JPYは小数点以下を許さない、USD/EURは2桁まで、というルールは個別フィールドの制約ではなく、両フィールドを合わせて初めて判定できるクロスフィールドルールです。

```java
enum Currency { JPY, USD, EUR }
record Money(BigDecimal amount, Currency currency) {}

var moneyDec = combine(
        field("amount",   decimal()),
        field("currency", enumOf(Currency.class))
).flatMap((amount, currency) -> {
    // 通貨ごとに許容する小数点以下桁数が異なる
    int maxScale = switch (currency) {
        case JPY -> 0;
        case USD, EUR -> 2;
    };
    if (amount.scale() > maxScale)
        return Result.fail("invalid_scale",
                currency + " does not allow more than " + maxScale + " decimal places");
    if (amount.compareTo(BigDecimal.ZERO) < 0)
        return Result.fail("negative_amount", "amount must not be negative");
    return Result.ok(new Money(amount, currency));
});

moneyDec.decode(Map.of("amount", new BigDecimal("1000"), "currency", "jpy"))
// ==> Ok[Money[amount=1000, currency=JPY]]

moneyDec.decode(Map.of("amount", new BigDecimal("10.5"), "currency", "jpy"))
// ==> Err[/: JPY does not allow more than 0 decimal places]

moneyDec.decode(Map.of("amount", new BigDecimal("9.99"), "currency", "usd"))
// ==> Ok[Money[amount=9.99, currency=USD]]
```

---

## 26. CSVインポート — 行番号付きエラー蓄積

**シーン:** 会員一括登録やデータ移行では、CSVファイルをアップロードしてもらう機能がよくあります。このとき、1行目でエラーが見つかっても即座に止めずに全行を検査して「2行目のemailが不正、4行目のageが範囲外」のように一度にフィードバックすることが、ユーザーにとって親切です。`Result.traverse` がこのパターンにフィットします。

```java
record MemberImport(String email, String name, int age) {}

var memberDec = combine(
        field("email", string().trim().toLowerCase().email()),
        field("name",  string().trim().nonBlank().maxLength(100)),
        field("age",   int_().range(0, 150))
).map(MemberImport::new);

// CSVをパースしてMapのリストとして渡す想定
var rows = List.<Map<String, Object>>of(
        Map.of("email", "alice@example.com", "name", "Alice", "age", 30),
        Map.of("email", "bad-email",          "name", "",      "age", 200),
        Map.of("email", "bob@example.com",   "name", "Bob",   "age", 25),
        Map.of("email", "also-bad",           "name", "Carol", "age", -1)
);

var result = Result.traverse(rows, memberDec::decode, Path.of("rows"));
switch (result) {
    case Ok(var members) -> System.out.println("Imported: " + members.size());
    case Err(var issues) -> issues.flatten().forEach((path, msgs) ->
            System.out.println(path + ": " + msgs));
}
// /rows/1/email: [not a valid email]
// /rows/1/name:  [is required]
// /rows/1/age:   [must be between 0 and 150]
// /rows/3/email: [not a valid email]
// /rows/3/age:   [must be between 0 and 150]
```

行番号がパスに含まれるため、UIやエラーレポートでどの行に問題があるかを直接ユーザーに返せます。エラーが複数行に渡っても、1回のデコードで全エラーが揃います。

---

## 27. 設定ファイルのデコード — ネスト構造 + withDefault

**シーン:** アプリケーション設定ファイル（YAML/TOMLをMapにパースしたもの）を読み込む場面では、「DBの接続先は必須だが、キャッシュ設定はなければデフォルトで動かしたい」「ログレベルは省略時はINFOで十分」のように、必須セクションとオプショナルセクションが混在します。型安全にデコードすることで、設定ミスをアプリ起動時に検出できます。

```java
record DbConfig(String host, int port, String database) {}
record CacheConfig(String host, int port, int ttlSeconds) {}
record AppConfig(DbConfig db, CacheConfig cache, String logLevel) {}

var dbConfigDec = combine(
        field("host",     string().nonBlank()),
        withDefault(field("port", int_().range(1, 65535)), 5432),
        field("database", string().nonBlank())
).map(DbConfig::new);

var cacheConfigDec = combine(
        withDefault(field("host",       string().nonBlank()), "localhost"),
        withDefault(field("port",       int_().range(1, 65535)), 6379),
        withDefault(field("ttlSeconds", int_().positive()), 300)
).map(CacheConfig::new);

var appConfigDec = combine(
        field("db",    nested(dbConfigDec)),
        // cache セクション自体が省略可能 — なければデフォルト設定を使う
        withDefault(
                field("cache", nested(cacheConfigDec)),
                new CacheConfig("localhost", 6379, 300)),
        withDefault(field("logLevel", string().nonBlank()), "INFO")
).map(AppConfig::new);

// db のみ指定 — その他はデフォルト
appConfigDec.decode(Map.of(
        "db", Map.of("host", "db.example.com", "database", "myapp")
))
// ==> Ok[AppConfig[db=DbConfig[host=db.example.com, port=5432, database=myapp],
//                  cache=CacheConfig[host=localhost, port=6379, ttlSeconds=300],
//                  logLevel=INFO]]

// db の host が空文字 — 起動時にエラーで検出
appConfigDec.decode(Map.of(
        "db", Map.of("host", "", "database", "myapp")
))
// ==> Err[/db/host: is required]
```

設定デコーダーをアプリ起動時に実行することで、環境変数の設定漏れや型ミスを本番コードに入る前に検出できます。
