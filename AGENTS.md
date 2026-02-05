# Repository Guidelines

## Project Structure & Module Organization
- `backend/` is a Spring Boot (Kotlin) service. Main code lives in `backend/src/main/kotlin/local/webterminal`, organized by `controller/`, `service/`, `repository/`, `entity/`, `dto/`, and `config/`.
- Backend configuration lives in `backend/src/main/resources/application.properties` (localhost binding and WebSocket settings).
- `frontend/` is a Create React App UI. Source is in `frontend/src` with UI components in `frontend/src/components` and static assets in `frontend/public`.
- Build outputs live in `frontend/build`; avoid manual edits there. The app can be served by Spring if you copy the build into `backend/src/main/resources/static`.
- Conversation logs are written to `backend/conversation.md` during terminal sessions.

## Build, Test, and Development Commands
Backend (from `backend/`):
- `./gradlew bootRun` starts the API/WebSocket server on `http://localhost:8080`.
- `./gradlew build` builds the service.
- `./gradlew test` runs backend tests.

Frontend (from `frontend/`):
- `npm start` runs the dev server at `http://localhost:3000` with a proxy to `http://localhost:8080`.
- `npm run build` creates a production build in `frontend/build`.
- `npm test` runs the React test runner.

## Coding Style & Naming Conventions
- Kotlin uses 4-space indentation and PascalCase for classes (e.g., `TerminalWsHandler`).
- JavaScript/React uses 2-space indentation and PascalCase for components (e.g., `TerminalCard.jsx`).
- Keep file and symbol names consistent with existing folders (`controller`, `service`, `repository`).
- No repository-specific formatter configuration is present; follow Kotlin and CRA defaults.
- API 요청은 `frontend/src/apiClient.js`의 요청 헬퍼를 사용한다.

## Testing Guidelines
- Backend tests use JUnit via Spring Boot. Prefer `*Test.kt` naming in `backend/src/test/kotlin` when adding tests.
- Frontend tests run with CRA/Jest; use `*.test.js` or `*.spec.js` under `frontend/src`.
- No coverage thresholds are configured.

## Commit & Pull Request Guidelines
- Git history is not available in this workspace, so no commit convention can be inferred. If unsure, use concise, imperative messages (e.g., `Add WebSocket auth check`).
- Pull requests should describe the change, include repro steps, and add screenshots for UI changes.

## Security & Configuration Tips
- This app is intended for local use only; the backend binds to `127.0.0.1`.
- WebSocket endpoint is `/ws/terminal`. Keep it local and avoid exposing it publicly.
- Database settings are in `backend/src/main/resources/application.properties` (PostgreSQL runtime dependency).
