# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

웹 터미널 애플리케이션의 Spring Boot 백엔드. pty4j(ConPTY)를 통해 Windows cmd.exe에 WebSocket 기반 PTY 접근을 제공. 로컬 전용.

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 개발 서버 실행 (127.0.0.1:8080에 바인딩)
./gradlew bootRun

# 테스트 실행
./gradlew test
```

## 아키텍처

```
local.webterminal
├── WebTerminalApplication.java  # Spring Boot 진입점
├── WsConfig.java                # WebSocket 설정, /ws/terminal 엔드포인트 등록
├── TerminalWsHandler.java       # WebSocket 핸들러 - PtyProcess 생성, I/O 브릿지
└── ConversationLogger.java      # user/codex 라인을 conversation.md에 기록
```

**데이터 흐름:**
1. 클라이언트가 `ws://localhost:8080/ws/terminal`에 연결
2. `TerminalWsHandler`가 pty4j를 통해 `cmd.exe` 생성
3. 클라이언트 입력 → PtyProcess stdin; PtyProcess stdout → 클라이언트
4. 리사이즈 메시지(`{type:"resize", cols:N, rows:N}`)로 터미널 크기 조정
5. 사용자 명령과 codex 접두사 출력이 `conversation.md`에 로깅됨

## 주요 설정

- 서버는 `127.0.0.1:8080`에 바인딩 (`application.properties` 참조)
- WebSocket 허용 origin: localhost:8080, localhost:3000
- Gradle 사용 (Maven 아님)

## TerminalWsHandler 상세

WebSocket을 통해 클라이언트와 Windows cmd.exe 프로세스 간의 양방향 통신을 처리.

### 라이프사이클

1. **연결 수립** (`afterConnectionEstablished`): cmd.exe 프로세스 생성 → stdout 리더 스레드 시작
2. **메시지 수신** (`handleTextMessage`): JSON 파싱 → resize 또는 input 처리
3. **연결 종료** (`afterConnectionClosed`): 프로세스 종료 → 스레드 풀 정리

### 메시지 프로토콜

```json
// 입력 메시지
{"type": "input", "data": "ls -la\r"}

// 리사이즈 메시지
{"type": "resize", "cols": 120, "rows": 40}
```

### 세션 속성

| 속성 | 설명 |
|------|------|
| `ATTR_PROCESS` | PtyProcess 인스턴스 |
| `ATTR_IOPOOL` | I/O 스레드 풀 (ExecutorService) |
| `ATTR_INPUT_BUFFER` | 사용자 입력 버퍼 |
| `ATTR_LAST_LOGGED` | 중복 로깅 방지용 |

### 데이터 흐름

```
┌──────────┐    WebSocket     ┌───────────────────┐    stdin     ┌─────────┐
│  Client  │ ───────────────► │ TerminalWsHandler │ ───────────► │ cmd.exe │
│ (React)  │ ◄─────────────── │                   │ ◄─────────── │  (PTY)  │
└──────────┘    TextMessage   └───────────────────┘    stdout    └─────────┘
                                       │
                                       ▼
                              ┌─────────────────┐
                              │ conversation.md │
                              └─────────────────┘
```
