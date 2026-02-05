# Gemini 작업 로그

| 시간 | 작업 내용 | 작업 이유 |
| --- | --- | --- |
| 2026년 2월 5일 | `gemini.md` 파일 생성 | 사용자의 요청에 따라 작업 내역을 기록하기 위해 |
| 2026년 2월 5일 | `build.gradle` 파일에 `spring-boot-starter-data-jpa`, `h2` 의존성 추가 | `User` 엔티티를 데이터베이스에 저장하고 관리하기 위해 JPA와 H2 인메모리 데이터베이스가 필요 |
| 2026년 2월 5일 | `User.java` 엔티티 클래스 생성 | 사용자 정보를 나타내는 데이터 모델(id, username, password)을 정의하기 위해 |
| 2026년 2월 5일 | `UserRepository.java` 리포지토리 인터페이스 생성 | `User` 엔티티에 대한 데이터베이스 CRUD(생성, 읽기, 수정, 삭제) 작업을 쉽게 처리하기 위해 |
| 2026년 2월 5일 | `application.properties` 파일 수정 | 개발 중 H2 데이터베이스 상태를 웹 콘솔(`http://localhost:8080/h2-console`)에서 쉽게 확인하고 관리할 수 있도록 설정 |
| 2026년 2월 5일 | `build.gradle` 파일 수정 (H2 -> PostgreSQL) | 사용자의 요청에 따라 데이터베이스를 H2에서 PostgreSQL로 변경하기 위해 |
| 2026년 2월 5일 | `application.properties` 파일 수정 (PostgreSQL 및 환경변수 사용) | 데이터베이스 설정을 PostgreSQL로 변경하고, 보안 및 구성 유연성을 위해 연결 정보를 환경 변수에서 읽어오도록 수정 |
| 2026년 2월 5일 | 패키지 구조 리팩토링 (`entity`, `repository`, `config`, `controller`, `service`, `util` 생성) | 코드의 유지보수성 및 가독성 향상을 위해 계층별로 패키지를 분리 |
| 2026년 2월 5일 | `Team`, `Member` 엔티티 및 `MemberRole` enum 생성 및 `User` 엔티티 업데이트 | User-Team-Member 간의 1:N 관계를 갖는 데이터 모델을 구현하고, 기존 User 엔티티에 Team 관계를 추가 |
| 2026년 2월 5일 | User-Team-Member 조회 API 개발 (`service`, `controller`, DTO 추가) | User ID를 기반으로 Team 및 Member 정보를 조회하는 API를 개발하여 데이터 조회 기능 확장 및 계층별 책임 분리 |
| 2026년 2월 5일 | 백엔드 프로젝트 Java -> Kotlin 전환 | 프로젝트의 현대화 및 Kotlin의 기능적 이점 활용 (안전성, 간결성 등)을 위해 백엔드 코드를 Kotlin으로 전환 |