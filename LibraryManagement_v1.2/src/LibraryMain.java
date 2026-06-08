import java.util.*;

/**
 * 도서 관리 시스템의 메인 클래스
 * <p>사용자 인터페이스(CLI)를 제공하며, DB 연결하여 권한에 따른 메뉴 출력 및 사용자 입력을 처리합니다.</p>
 *
 * @author Su Man Nam
 * @version 1.2
 * <p>2026년 5월 13일 시작</p>
 */
public class LibraryMain {
    private static LibraryManager manager;
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        LibraryRepository repo = new LibraryRepository();
        manager = new LibraryManager(repo);
        manager.initialize();

        if (!performLogin())
            return;

        User user = manager.getCurrentUser();
        System.out.println("로그인 성공! 권한: " + user.getRole());

        while (true) {
            if (user.isAdmin()) showAdminMenu();
            else showUserMenu();

            System.out.print("  명령 입력: ");
            int choice = sc.nextInt();
            sc.nextLine();

            if (choice == 0) {
                handleExit();
                break;
            }
            processCommand(choice, user.getRole());
        }
    }

    /**
     * 사용자 로그인을 수행합니다.
     * <p>성공할 때까지 아이디와 비밀번호 입력을 반복 요청합니다.</p>
     *
     * @return 로그인 성공 여부 (true: 성공)
     * @see LibraryManager#login(String, String)
     */
    private static boolean performLogin() {
        while (true) {
            System.out.println("\n========= CSV 로그인 시스템 =========");
            System.out.print("아이디: ");
            String id = sc.nextLine();
            if (Character.isDigit(id.charAt(0))) {
                System.out.println("다시 입력하세요.");
                continue;
            }
            System.out.print("비밀번호: ");
            String pw = sc.nextLine();

            if (manager.login(id, pw)) return true;
            System.out.println("[오류] 아이디 또는 비밀번호가 틀렸습니다.");
        }
    }

    /**
     * 입력된 선택 번호와 사용자 권한에 따라 적절한 UI 기능을 호출합니다.
     *
     * @param choice 메뉴 선택 번호
     * @param role   사용자 권한 (ADMIN 또는 USER)
     */
    private static void processCommand(int choice, String role) {
        if (role.equals("ADMIN")) {
            switch (choice) {
                case 1 -> addBookUI();
                case 2 -> editOrDeleteUI();
                case 5 -> listBooksUI();
                case 6 -> searchBookUI();
                case 9 -> checkServerUI();
            }
        } else {
            switch (choice) {
                case 1 -> borrowBookUI();
                case 2 -> returnBookUI();
                case 3 -> showLoanStatusUI();
                case 5 -> listBooksUI();
                case 6 -> searchBookUI();
            }
        }
    }

    /**
     * 프로그램 종료를 처리합니다.
     * <p>사용자 확인 후, 변경된 모든 데이터를 MariaDB에 동기화하고 종료합니다.</p>
     *
     * @see LibraryManager#saveChanges()
     * @see <a href="https://github.com/sumannam/Java/issues/22">Issue #22: 종료 시 데이터 누락 방지</a>
     */
    private static void handleExit() {
        System.out.print("정말로 종료하시겠습니까? [Y/n]: ");
        if (sc.nextLine().equalsIgnoreCase("y")) {
            manager.saveChanges();
            System.out.println("데이터 저장 완료. 감사합니다.");
            System.exit(0);
        }
    }

    // (나머지 UI 메서드들: listBooks, borrowBook 등 기존 코드와 거의 동일하게 구현)
    private static void showAdminMenu() {
        System.out.println("===========================================================");
        System.out.println("          [ 관리자 전용 메뉴 ]");
        System.out.println("===========================================================");
        System.out.println("  1. 도서 등록 (Add)");
        System.out.println("  2. 도서 수정 및 삭제 (Edit/Delete)");
        System.out.println("  5. 전체 도서 목록 (List)");
        System.out.println("  6. 도서 검색 (Search)");
        System.out.println("  9. 서버 상태 점검 (Check)");
        System.out.println("  0. 종료 (Exit)");
    }

    private static void showUserMenu() {
        System.out.println("===========================================================");
        System.out.println("          [ 일반 사용자 메뉴 ]");
        System.out.println("===========================================================");
        System.out.println("  1. 도서 대출 (Borrow)");
        System.out.println("  2. 도서 반납 (Return)");
        System.out.println("  3. 대출 현황 보기 (Status)");
        System.out.println("  5. 전체 도서 목록 (List)");
        System.out.println("  6. 도서 검색 (Search)");
        System.out.println("  0. 종료 (Exit)");
    }

    /**
     * 신규 도서 등록 입력을 처리합니다.
     * <p>제목과 저자를 입력받아 유효성 검사 후 시스템에 등록합니다.</p>
     *
     * @see LibraryManager#addBook(String, String)
     */
    private static void addBookUI() {
        System.out.println("\n[도서 등록]");
        System.out.print("- 제목 입력: ");
        String title = sc.nextLine().trim();
        System.out.print("- 저자 입력: ");
        String author = sc.nextLine().trim();

        if (title.isEmpty() || author.isEmpty()) {
            System.out.println("[오류] 제목과 저자명은 공백일 수 없습니다.");
            return;
        }
        manager.addBook(title, author);

        // DB 저장
        manager.saveChanges();
    }

    /**
     * 도서 정보의 수정 및 삭제를 처리하는 UI입니다.
     * <p>ID를 통해 도서를 조회하고, 선택에 따라 제목/저자 수정 또는 삭제를 수행합니다.</p>
     * <p>수정과 삭제의 효율화를 위해 같은 기능으로 개발(26.05.20; 양혁주)</p>
     * @see <a href="https://github.com/hyckju/LibraryManagement/issues/1">Issue #1: 한 책 삭제 시 DB에서 해당 책이 삭제 안됨</a>
     * @see LibraryManager#deleteBook(int)
     */
    private static void editOrDeleteUI() {
        System.out.println("\n[도서 수정 및 삭제]");
        System.out.print("- 관리할 도서 ID 입력: ");
        if (!sc.hasNextInt()) {
            System.out.println("[오류] 숫자만 입력 가능합니다.");
            sc.nextLine();
            return;
        }
        int id = sc.nextInt();
        sc.nextLine();

        // Manager를 통해 도서 존재 확인
        Book book = manager.getBookMap().get(id);
        if (book == null) {
            System.out.println("[오류] 해당 ID의 도서가 존재하지 않습니다.");
            return;
        }

        System.out.println("-----------------------------------------------------------");
        System.out.printf("  현재 정보: [%s | %s | %s]\n",
                book.getTitle(), book.getAuthor(), book.isAvailable() ? "비치중" : "대출중");
        System.out.println("  1. 제목 수정  2. 저자 수정  3. 도서 삭제  0. 취소");
        System.out.println("-----------------------------------------------------------");
        System.out.print("  선택: ");
        int choice = sc.nextInt();
        sc.nextLine();

        switch (choice) {
            case 1 -> {
                System.out.print("- 새 제목 입력하세요: ");
                String newTitle = sc.nextLine().trim();
                if (!newTitle.isEmpty()) {
                    book.setTitle(newTitle);
                    System.out.println("[결과] 제목이 수정되었습니다.");
                }manager.saveChanges();

            }
            case 2 -> {
                System.out.print("- 새 저자 입력: ");
                String newAuthor = sc.nextLine().trim();
                if (!newAuthor.isEmpty()) {
                    book.setAuthor(newAuthor);
                    System.out.println("[결과] 저자명이 수정되었습니다.");
                }manager.saveChanges();
            }
            case 3 -> {
                manager.deleteBook(id);
                //삭제 쿼리를 작성
                System.out.println("[결과] 삭제되었습니다.");


            }
        }

        // DB 저장
        manager.saveChanges();
    }

    /**
     * 도서 대출 입력을 처리합니다.
     * @see LibraryManager#borrowBook(int)
     */
    private static void borrowBookUI() {
        System.out.print("- 대출할 도서 ID 입력: ");
        int id = sc.nextInt();
        sc.nextLine();

        if (manager.borrowBook(id)) {
            System.out.println("[결과] 대출이 완료되었습니다.");
        } else {
            System.out.println("[오류] 대출할 수 없는 도서이거나 이미 대출 중입니다.");
        }

        // DB 저장
        manager.saveChanges();
    }

    /**
     * 도서 반납 입력을 처리합니다.
     * @see LibraryManager#returnBook(int)
     */
    private static void returnBookUI() {
        System.out.print("- 반납할 도서 ID 입력: ");
        int id = sc.nextInt();
        sc.nextLine();

        if (manager.returnBook(id)) {
            System.out.println("[결과] 반납이 완료되었습니다.");
        } else {
            System.out.println("[오류] 반납할 수 없는 도서입니다.");
        }

        // DB 저장
        manager.saveChanges();
    }

    /**
     * 전체 도서 목록을 테이블 형식으로 화면에 출력합니다.
     * <p>등록된 모든 도서의 ID, 제목, 저자 및 대출 가능 여부를 정렬된 형태로 표시합니다.</p>
     * * @see LibraryManager#getAllBooks()
     */
    private static void listBooksUI() {
        System.out.println("===========================================================");
        System.out.println(" [도서 목록]");
        System.out.printf(" %-5s | %-12s | %-10s | %-10s \n", "ID", "제목", "저자", "상태");
        System.out.println("-----------------------------------------------------------");

        Collection<Book> books = manager.getAllBooks();
        if (books.isEmpty()) {
            System.out.println("  등록된 도서가 없습니다.");
        } else {
            for (Book b : books) {
                String status = b.isAvailable() ? "대출 가능" : "대출 중";
                System.out.printf(" %-5d | %-12s | %-10s | %-10s \n",
                        b.getId(), b.getTitle(), b.getAuthor(), status);
            }
        }
        System.out.println("===========================================================");
    }

    /**
     * 제목 키워드를 입력받아 검색 결과를 출력합니다.
     * <p>사용자로부터 검색어를 입력받아 해당 키워드가 포함된 도서 목록을 필터링하여 보여줍니다.</p>
     * * @see LibraryManager#searchBook(String)
     */
    private static void searchBookUI() {
        System.out.print("- 검색할 제목 키워드 입력: ");
        String keyword = sc.nextLine().trim();
        List<Book> results = manager.searchBook(keyword);

        System.out.printf(" 검색 결과 (%d건)\n", results.size());
        for (Book b : results) {
            System.out.printf(" %-5d | %-12s | %-10s | %-10s \n",
                    b.getId(), b.getTitle(), b.getAuthor(), b.isAvailable() ? "가능" : "대출중");
        }
    }

    /**
     * 현재 시스템의 도서 대출 현황을 상세히 조회합니다.
     * <p>대출 중인 도서들에 대해 도서 정보와 현재 대출자(member_id)의 정보를 대조하여 출력합니다.</p>
     * * @see LibraryManager#getAllBooks()
     * @see <a href="https://github.com/sumannam/Java/issues/23">Issue #23: 대출자 정보 매핑 확인</a>
     */
    private static void showLoanStatusUI() {
        System.out.println("\n[ 현재 도서 대출 현황 ]");
        boolean found = false;
        for (Book b : manager.getAllBooks()) {
            if (!b.isAvailable()) {
                System.out.printf("ID: %d | 제목: %s | 대출자: %s\n",
                        b.getId(), b.getTitle(), b.getBorrowerId());
                found = true;
            }
        }
        if (!found) System.out.println("대출 중인 도서가 없습니다.");
    }

    /**
     * 서버의 네트워크 상태를 진단하기 위한 인터페이스를 제공합니다.
     * <p>사용자로부터 IP 주소를 입력받아 {@link LibraryManager#checkServerStatus(String)}에 전달합니다.</p>
     * <p><b>보안 보완:</b> 기존에는 입력값을 검증 없이 쉘 명령어로 실행하여 {@code ;}, {@code &} 등을 이용한
     * OS Command Injection이 가능했으나, {@code checkServerStatus()}에서 IPv4 형식 검증과 ProcessBuilder 적용으로
     * 주입을 차단하도록 수정하였습니다. (26.06.01; 양혁주)</p>
     * @see LibraryManager#checkServerStatus(String)
     *
     * @see <a href="https://github.com/hyckju/LibraryManagement/issues/8">Issue #8: OS Command Injection 취약점 보완</a>
     * @see <a href="https://github.com/sumannam/Java/issues/43">Issue #43: OS Command Injection 취약점 개발</a>
     */
    private static void checkServerUI() {
        System.out.println("\n[서버 네트워크 진단]");
        System.out.print("- 접속을 확인 할 IP 주소를 입력하세요: ");
        String ip = sc.nextLine(); // IPv4 형식만 통과하도록 checkServerStatus()에서 검증됨

        // Manager에게 명령어 실행을 맡김
        manager.checkServerStatus(ip);
    }
}