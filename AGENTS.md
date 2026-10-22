# Project preferences

- Use Spectre only as interactive tooling to inspect and operate the running app.
- Do not create or run automated Compose/Spectre UI tests, JUnit UI drivers, or scripted UI test suites.
- Backend/unit tests are allowed. Keep them headless; they must not open app windows or drive the UI.
- Keep interactive tooling out of packaged production releases.
- Use KPM for supported dependency declarations; development-only and test configurations remain in Gradle.
