# web_terminal

Local-only web terminal for Windows (Spring Boot + WebSocket + PTY-backed cmd.exe + React UI).

## Run (dev)

```powershell
cd "C:\Users\DesignPC\Desktop\ai dashboard\team-manager\web_terminal"
.\mvnw spring-boot:run
```

Then open:

```
http://localhost:3000
```

## Notes
- Server binds to 127.0.0.1 only (see `src\main\resources\application.properties`).
- WebSocket endpoint: `/ws/terminal`.
- Uses ConPTY via pty4j so TTY-dependent apps can run.
- Appends chat logs to `conversation.md` in the project root as `user:` / `codex:` lines.
- This is intended for local use only.

## Frontend

```powershell
cd "C:\Users\DesignPC\Desktop\ai dashboard\team-manager\web_terminal\frontend"
npm start
```

## Optional: build React and serve from Spring
```powershell
cd "C:\Users\DesignPC\Desktop\ai dashboard\team-manager\web_terminal\frontend"
npm run build
```
Copy `frontend/build` contents into `src\main\resources\static` and restart Spring.
