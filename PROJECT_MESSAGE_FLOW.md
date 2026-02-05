# 프로젝트 메시지 흐름 (Project Message Flow)

이 문서는 `frontend` (React)와 `backend` (Spring Boot) 간의 WebSocket 통신 및 백엔드와 PTY(Pseudo-Terminal) 간의 상호작용에 대한 메시지 형식을 정의합니다.

## 1. 개요 (Overview)

-   **Frontend (React)**: `xterm.js`를 사용하여 터미널 UI를 제공하고, WebSocket을 통해 백엔드와 통신합니다.
-   **Backend (Spring Boot)**: WebSocket 연결(`ws://localhost:8080/ws/terminal`)을 처리하고, `pty4j` 라이브러리를 사용해 `cmd.exe` PTY 프로세스를 관리합니다.
-   **통신 형식**: 모든 통신은 JSON 형식의 메시지를 사용합니다.

```json
┌──────────┐      WebSocket (JSON)      ┌───────────────────┐      I/O      ┌─────────┐
│  React   │ ◄──────────────────────────► │   Spring Boot     │ ◄──────────► │   PTY   │
│ Frontend │ (xterm.js)                  │(TerminalWsHandler)│ (pty4j)     │ (cmd.exe) │
└──────────┘                             └───────────────────┘              └─────────┘
```

---

## 2. Frontend (React) → Backend (Spring)

클라이언트가 백엔드로 보내는 메시지 형식입니다.

### A. 사용자 입력 (User Input)

사용자가 터미널에 키보드 입력을 할 때 전송됩니다.

-   **형식**:
    ```json
    {
      "role": "input",
      "data": "사용자 입력 문자열 (예: 'ls -l\r')"
    }
    ```
-   **참고**: 레거시 형식인 `{ "type": "input", ... }`도 호환성을 위해 지원됩니다.

### B. 터미널 크기 조정 (Terminal Resize)

브라우저 창 크기가 변경되거나 `xterm.js` 인스턴스의 크기가 조정될 때 전송됩니다.

-   **형식 1 (주요 형식)**:
    ```json
    {
      "role": "resize",
      "data": {
        "cols": 80,
        "rows": 24
      }
    }
    ```
-   **형식 2 (레거시)**:
    ```json
    {
      "type": "resize",
      "cols": 80,
      "rows": 24
    }
    ```
-   **설명**: `cols`는 터미널의 너비(열), `rows`는 높이(행)를 나타냅니다.

---

## 3. Backend (Spring) → Frontend (React)

백엔드가 PTY 프로세스로부터 받은 출력을 클라이언트로 전달하는 메시지 형식입니다.

### A. 터미널 출력 (Terminal Output)

PTY 프로세스(`cmd.exe`)의 표준 출력을 그대로 클라이언트에 전달합니다. 이 데이터에는 ANSI 이스케이프 코드가 포함될 수 있습니다.

-   **형식**:
    ```json
    {
      "role": "terminal",
      "data": "PTY가 출력한 데이터 청크"
    }
    ```
-   **설명**: `xterm.js`는 이 데이터를 받아 터미널에 렌더링합니다.

### B. 역할 기반 메시지 (Role-based Messages)

백엔드는 터미널 출력에서 특정 역할(예: "Frontend Engineer")의 메시지를 파싱하여 별도의 메시지로 전달합니다.

-   **형식**:
    ```json
    {
      "role": "역할 이름 (예: Frontend Engineer)",
      "data": "해당 역할이 출력한 메시지 내용"
    }
    ```
-   **정의된 역할**: `Team Leader`, `Planner`, `Frontend Engineer`, `Backend Engineer`, `Designer`

### C. 준비 상태 알림 (Ready State Notification)

백엔드가 내부적으로 Claude 인스턴스가 준비되었다고 판단하면 클라이언트에 알립니다.

-   **형식**:
    ```json
    {
      "role": "ready",
      "data": "true"
    }
    ```
