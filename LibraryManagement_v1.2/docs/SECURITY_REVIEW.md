# 보안 취약점 점검 및 보완 개발 문서

- **대상**: LibraryManagement v1.2
- **작성일**: 2026-06-01
- **범위**: `src/` 전체 소스 코드 정적 점검
- **목적**: 발견된 보안 취약점을 정리하고, 보완 개발(remediation)을 위한 기준 문서로 활용

---

## 요약 (Summary)

| # | 취약점 | 위치 | 심각도 | 유형 | 비고 |
|---|--------|------|--------|------|------|
| 1 | SQL Injection | `LibraryRepository.java:148` | 🔴 Critical | 의도적(교육용) | 인증 우회 가능 / 현재 코드 동작 깨짐 |
| 2 | OS Command Injection | `LibraryManager.java:172` | 🔴 Critical | 의도적(교육용) | 임의 명령 실행 |
| 3 | 하드코딩된 DB 자격증명 | `DBconn.java:7-9`, `LibraryRepository.java:7-9` | 🟠 High | 부수적 | 평문·중복 |
| 4 | 비밀번호 평문 저장·비교 | `User.java`, `LibraryRepository.java:148` | 🟠 High | 부수적 | 해시 미적용 |
| 5 | 무차별 대입 방어 부재 | `LibraryMain.java:49` | 🟡 Medium | 부수적 | 시도 제한 없음 |
| 6 | 오류 메시지 정보 노출 | 다수 (`catch` 블록) | 🟡 Medium | 부수적 | 내부 정보 노출 |
| 7 | 권한 검증이 UI에만 의존 | `LibraryMain.java`, `LibraryManager.java` | 🟡 Medium | 부수적 | 접근 통제 결함 |

> ※ 1·2번은 소스 주석에 "보안 실습용 의도적 설계"로 명시됨. 안전한 버전이 주석/대안으로 일부 존재.

---

## 1. SQL Injection (의도적 / Critical)

**위치**: `LibraryRepository.java:148` `loadUser(String id, String pw)`

### 현재 코드
```java
//String sql = "SELECT * FROM users WHERE user_id = ? AND password = ?";
String sql = "'SELECT * FROM users WHERE user_id = '" + id + "'' AND password = '" + pw + " ' ";

try (Connection conn = getConnection();
     PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setString(1, id);
    pstmt.setString(2, pw);
    ...
```

### 문제점
- 사용자 입력(`id`, `pw`)이 쿼리 문자열에 직접 결합됨 → `' OR '1'='1` 등으로 **인증 우회** 가능.
- **추가 결함**: 결합된 쿼리에는 `?` 자리표시자가 없는데 `setString(1, id)`를 호출 → 실제로는 `SQLException` 발생하여 **현재 로그인 기능 자체가 동작하지 않음**.

### 보완 방안
- `PreparedStatement` 자리표시자(`?`)와 파라미터 바인딩 사용 (주석에 정답 존재).

```java
String sql = "SELECT * FROM users WHERE user_id = ? AND password = ?";

try (Connection conn = getConnection();
     PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setString(1, id);
    pstmt.setString(2, pw);   // 단, 비밀번호는 4번 항목(해시) 적용 후 해시값으로 비교
    try (ResultSet rs = pstmt.executeQuery()) {
        if (rs.next()) {
            return new User(rs.getString("user_id"), rs.getString("password"), rs.getString("type"));
        }
    }
}
```

### 체크리스트
- [x] 문자열 연결 쿼리 → PreparedStatement 바인딩으로 전환 (2026-06-01)
- [x] `SELECT *` → 필요한 컬럼만 명시 선택 (2026-06-01)
- [ ] 4번(비밀번호 해시) 적용 후 비교 로직 연동

---

## 2. OS Command Injection (의도적 / Critical)

**위치**: `LibraryManager.java:172` `checkServerStatus(String ip)`

### 현재 코드
```java
String command = "cmd.exe /c ping -n 1 " + ip;
Process process = Runtime.getRuntime().exec(command);
```

### 문제점
- IP 입력값 검증 없이 쉘 명령에 결합 → `127.0.0.1 && dir`, `127.0.0.1 & del ...` 등 **임의 명령 실행** 가능.

### 보완 방안
1. 입력값을 IP 형식으로 **검증**(화이트리스트/정규식).
2. 쉘(`cmd.exe /c`) 사용을 피하고 `ProcessBuilder`에 **인자를 배열로 분리** 전달.

```java
public void checkServerStatus(String ip) {
    // 1) 입력 검증: IPv4 형식만 허용
    if (!ip.matches("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")) {
        System.out.println("[오류] 유효한 IPv4 주소가 아닙니다.");
        return;
    }
    try {
        // 2) 쉘을 거치지 않고 인자 분리 전달
        ProcessBuilder pb = new ProcessBuilder("ping", "-n", "1", ip);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "EUC-KR"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        process.waitFor();
    } catch (Exception e) {
        System.out.println("[오류] 진단 중 예외 발생");
    }
}
```

### 체크리스트
- [x] IP 입력값 정규식 검증 추가 (2026-06-01)
- [x] `Runtime.exec(String)` → `ProcessBuilder(인자 배열)` 전환, 쉘(`cmd.exe /c`) 제거 (2026-06-01)
- [x] 프로세스 스트림 try-with-resources 처리 및 `waitFor()` 호출 (2026-06-01)

---

## 3. 하드코딩된 DB 자격증명 (High)

**위치**: `DBconn.java:7-9`, `LibraryRepository.java:7-9`

### 현재 코드
```java
private static final String URL = "jdbc:mariadb://192.168.100.20:3306/library";
private static final String USER = "cjulib";
private static final String PASSWORD = "security";
```

### 문제점
- DB 접속 정보가 소스에 **평문**으로 존재하고 **두 파일에 중복**됨.
- Git에 커밋되면 이력에 영구 노출. 비밀번호 변경 시 누락 위험.

### 보완 방안
- 환경변수 / 외부 설정파일(`config.properties`, `.env`) / 시크릿 매니저로 분리.
- 접속 정보 단일 출처화(`DBconn` 한 곳으로 통합, `LibraryRepository`는 이를 재사용).

```java
// 예시: 환경변수 사용
private static final String URL = System.getenv("DB_URL");
private static final String USER = System.getenv("DB_USER");
private static final String PASSWORD = System.getenv("DB_PASSWORD");
```

### 체크리스트
- [ ] 자격증명을 코드 밖(환경변수/설정)으로 분리
- [ ] 설정 파일은 `.gitignore`에 추가
- [ ] DB 접속 정보 중복 제거(단일 출처화)
- [ ] (권장) 이미 커밋된 비밀번호는 노출된 것으로 간주, DB 계정 비밀번호 변경(rotation)

---

## 4. 비밀번호 평문 저장·비교 (High)

**위치**: `User.java`, `LibraryRepository.java:148`

### 문제점
- 비밀번호를 해시 없이 DB에 **평문 저장**하고 평문 비교.
- `User`가 `password`를 그대로 보관하며 `getPassword()`로 노출 → 로그인 후에도 메모리에 평문 유지.

### 보완 방안
- 저장 시 솔트 기반 단방향 해시(BCrypt, Argon2 등) 사용, 검증 시 해시 비교.
- `User` 객체에서 password 필드/게터 제거(인증 후 보관 불필요).

```java
// 가입/저장 시
String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
// 로그인 검증 시 (DB에서 해시 조회 후)
boolean ok = BCrypt.checkpw(inputPassword, storedHash);
```

### 체크리스트
- [ ] 비밀번호 해시 라이브러리 도입(BCrypt/Argon2)
- [ ] 기존 평문 비밀번호 마이그레이션 계획 수립
- [ ] `User`에서 password 보관 제거

---

## 5. 무차별 대입(brute-force) 방어 부재 (Medium)

**위치**: `LibraryMain.java:49` `performLogin()`

### 문제점
- 시도 횟수 제한·지연·계정 잠금 없이 로그인 무한 반복 → 자동화 대입 공격에 취약.

### 보완 방안
- 연속 실패 횟수 제한(예: 5회) 후 일정 시간 잠금 또는 지연(backoff) 적용.
- (서버 환경이라면) 계정별 실패 카운트 및 잠금 정책 도입.

### 체크리스트
- [ ] 로그인 실패 횟수 카운트 및 임계치 적용
- [ ] 임계 초과 시 지연/잠금 처리

---

## 6. 오류 메시지를 통한 정보 노출 (Medium)

**위치**: 다수 (`LibraryRepository`, `LibraryManager`, `DBconn`의 `catch` 블록)

### 현재 코드 예시
```java
System.err.println("[오류] DB 연결 실패: " + e.getMessage());
```

### 문제점
- `Exception.getMessage()`를 그대로 출력 → DB 구조·접속 정보 등 내부 정보 노출.
- SQL Injection 공격자에게 단서 제공(error-based injection).

### 보완 방안
- 사용자에게는 일반화된 메시지만 노출, 상세 내용은 내부 로그(파일/로깅 프레임워크)로 분리.

```java
catch (SQLException e) {
    logger.error("DB 연결 실패", e);          // 내부 로그
    System.err.println("[오류] 처리 중 문제가 발생했습니다."); // 사용자
}
```

### 체크리스트
- [ ] 사용자 노출 메시지와 내부 로그 분리
- [ ] 로깅 프레임워크(예: SLF4J/Logback) 도입 검토

---

## 7. 권한 검증이 UI 메뉴에만 의존 (Medium)

**위치**: `LibraryMain.java` `processCommand()`, `LibraryManager.java` 핵심 메서드

### 문제점
- 관리자/일반 권한 구분이 화면 메뉴 분기에만 존재.
- `deleteBook`, `addBook` 등 핵심 메서드 자체에는 권한 체크가 없어, 호출 경로 변경 시 권한 우회 가능.

### 보완 방안
- 핵심 비즈니스 메서드 진입부에서 `currentUser` 권한을 직접 검증(서버측 통제 원칙).

```java
public boolean deleteBook(int id) {
    if (currentUser == null || !currentUser.isAdmin()) {
        throw new SecurityException("권한이 없습니다.");
    }
    repository.deleteBook(id);
    return bookMap.remove(id) != null;
}
```

### 체크리스트
- [ ] 관리자 전용 기능(추가/수정/삭제)에 권한 검증 추가
- [ ] UI 분기와 별개로 도메인 계층에서 통제 일원화

---

## 권장 보완 순서

1. **1, 2번 (Critical)** — SQL Injection, OS Command Injection 우선 차단
2. **3, 4번 (High)** — 자격증명 분리, 비밀번호 해시화
3. **5, 6, 7번 (Medium)** — 대입 방어, 오류 메시지, 권한 통제

## 참고 표준
- OWASP Top 10: A03 Injection, A07 Identification and Authentication Failures, A02 Cryptographic Failures, A01 Broken Access Control
- CWE-89 (SQL Injection), CWE-78 (OS Command Injection), CWE-798 (Hardcoded Credentials), CWE-256/CWE-916 (Plaintext/Weak Password Storage)
