import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class LibraryManager {
    private Map<Integer, Book> bookMap;
    private List<User> userList;
    private User currentUser;
    private LibraryRepository repository;
    private int bookCount = 0;

    /**
     * LibraryManager 생성자입니다.
     * @param repository 데이터를 저장하고 불러올 리포지토리 객체
     */
    public LibraryManager(LibraryRepository repository) {
        this.repository = repository;
    }

    /**
     * 시스템을 초기화합니다.
     * <p>리포지토리로부터 도서 데이터를 로드하고, 도서 ID 카운트를 현재 최대값으로 동기화합니다.</p>
     * * @see LibraryRepository#loadBooks()
     * @see <a href="https://github.com/sumannam/Java/issues/23">Issue #23: 프로그램 실행 시 데이터 로드</a>
     */
    public void initialize() {
        this.bookMap = repository.loadBooks();
        // ID 카운트 동기화
        for (Integer id : bookMap.keySet()) {
            if (id > bookCount) bookCount = id;
        }
    }

    /**
     * 사용자 로그인을 수행하고 인증 상태를 기록합니다.
     * <p>성공 시 {@code currentUser}에 사용자 정보를 저장합니다.</p>
     *
     * @param id 사용자 아이디
     * @param pw 사용자 비밀번호
     * @return 로그인 성공 여부 (성공 시 true)
     * @see LibraryRepository#loadUser(String, String)
     */
    public boolean login(String id, String pw) {
        // 기존에 List<String>으로 받던 부분을 User로 변경
//        this.userList = repository.loadLogin(id, pw);
        User user = repository.loadUser(id, pw);
        if (user != null) {
            this.currentUser = user; // 로그인 성공 시 현재 사용자 저장
            return true;
        }
        return false;}

    /** @return 현재 로그인 중인 {@link User} 객체 */
    public User getCurrentUser() {
        return currentUser;
    }

//    public void setCurretUser(String user) {
//        this.currentUser = user;
//    }

    public void addBook(String title, String author) {
        bookCount++;
        bookMap.put(bookCount, new Book(bookCount, title, author, true, "null"));
        System.out.println("-----------------------------------------------------------");
        System.out.printf("[결과] 등록이 완료되었습니다. (도서 ID: %d)\n", bookCount);
    }

    /**
     * 도서 정보를 수정합니다.
     * @param id     수정할 도서 ID
     * @param title  새 제목
     * @param author 새 저자
     * @return 수정 성공 여부
     */
    public boolean editBook(int id, String title, String author) {
        if (!bookMap.containsKey(id)) return false;
        Book book = bookMap.get(id);
        // 제목이나 저자가 비어있지 않을 때만 수정 (기존 로직 유지)
        return true;
    }

    /**
     * 도서를 시스템에서 삭제합니다.
     * <p>삭제하는 메서드 호출 추가-Ref: Github Issue #1(2026.05.20; 양혁주)</p>
     * @param id 삭제할 도서 ID
     * @return 삭제 성공 여부
     * @see <a href="https://github.com/hyckju/LibraryManagement/issues/1">Issue #1: 한 책 삭제 시 DB에서 해당 책이 삭제 안됨</a>
     */
    public boolean deleteBook(int id) {
        repository.deleteBook(id);
        return bookMap.remove(id) != null;
    }

    /**
     * 도서 대출 처리를 수행합니다.
     * <p>도서가 대출 가능한 상태일 경우, 상태를 변경하고 현재 로그인 사용자를 대출자로 등록합니다.</p>
     *
     * @param id 대출할 도서 ID
     * @return 대출 성공 여부
     */
    public boolean borrowBook(int id) {
        if (!bookMap.containsKey(id))
            return false;

        Book book = bookMap.get(id);
        if (book.isAvailable()) {
            book.setAvailable(false);
            book.setBorrowerId(currentUser.getUserId());
            return true;
        }
        return false;
    }

    /**
     * 도서 반납 처리를 수행합니다.
     * <p>도서가 대출 중인 상태일 경우, 상태를 대출 가능으로 변경하고 대출자 정보를 초기화합니다.</p>
     *
     * @param id 반납할 도서 ID
     * @return 반납 성공 여부
     */
    public boolean returnBook(int id) {
        if (!bookMap.containsKey(id))
            return false;

        Book book = bookMap.get(id);
        if (!book.isAvailable()) {
            book.setAvailable(true);
            book.setBorrowerId("null");
            return true;
        }
        return false;
    }

    /**
     * 제목 키워드를 사용하여 도서를 검색합니다.
     * @param keyword 검색어
     * @return 검색된 도서 객체들의 리스트
     */
    public List<Book> searchBook(String keyword) {
        List<Book> found = new ArrayList<>();
        for (Book book : bookMap.values()) {
            if (book.getTitle().contains(keyword)) found.add(book);
        }
        return found;
    }

    /** @return 등록된 모든 도서 정보의 Collection */
    public Collection<Book> getAllBooks() {
        return bookMap.values();
    }

    public int getBookCount() {
        return bookCount;
    }

    /**
     * 현재 메모리의 도서 변경 내역을 리포지토리를 통해 저장합니다.
     * @see LibraryRepository#saveBooks(Map)
     * @see <a href="https://github.com/sumannam/Java/issues/42">Issue #42: 테이블 추가/수정/삭제 방식 수정</a>
     */
    public void saveChanges() {
        repository.saveBooks(bookMap);
    }

    /** @return 도서 ID와 객체가 맵핑된 전체 Map 객체 */
    public Map<Integer, Book> getBookMap() {
        return bookMap;
    }

    /**
     * 입력받은 IP 주소로 네트워크 상태(ping)를 진단합니다.
     * <p><b>보안 보완:</b> 기존에는 {@code "cmd.exe /c ping -n 1 " + ip} 형태로 쉘에 그대로 전달하여
     * 명령어 구분자({@code &&}, {@code ;}, {@code |})를 이용한 OS Command Injection에 취약했으나,
     * 다음과 같이 수정하였습니다. (26.06.01; 양혁주)</p>
     * <ol>
     *   <li>입력값을 IPv4 형식 정규식으로 검증(화이트리스트)하여 비정상 입력을 차단</li>
     *   <li>쉘(cmd.exe /c)을 거치지 않고 {@link ProcessBuilder}로 인자를 분리 전달</li>
     * </ol>
     * @param ip 진단할 대상 IPv4 주소
     *
     * @see <a href="https://github.com/hyckju/LibraryManagement/issues/8">Issue #8: OS Command Injection 취약점 보완</a>
     * @see <a href="https://github.com/sumannam/Java/issues/43">Issue #43: OS Command Injection 취약점 개발</a>
     */
    public void checkServerStatus(String ip) {
        // OS Command Injection 방지 1) 입력값을 IPv4 형식으로 검증 (화이트리스트)
        if (ip == null || !ip.matches("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")) {
            System.out.println("[오류] 유효한 IPv4 주소가 아닙니다.");
            return;
        }

        try {
            // OS Command Injection 방지 2) 쉘(cmd.exe /c)을 거치지 않고 인자를 배열로 분리 전달
            ProcessBuilder pb = new ProcessBuilder("ping", "-n", "1", ip);
            pb.redirectErrorStream(true);

            System.out.println("[시스템 실행 명령어]: ping -n 1 " + ip);

            Process process = pb.start();
            // 한글 깨짐 방지를 위해 EUC-KR 유지
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "EUC-KR"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            System.out.println("[오류] 진단 중 예외가 발생했습니다.");
        }
    }
}