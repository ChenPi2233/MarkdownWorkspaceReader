# Phone Test Checklist

## APK

- Build from the project root with `.\gradlew.bat :app:assembleDebug`.
- APK path: `app/build/outputs/apk/debug/app-debug.apk`.
- Install with `.\tools\install-debug.ps1` after the phone is authorized for USB debugging.

## Needed Repository Inputs

- `owner/repo`
- `branch`
- `rootPath`, such as `docs`, or blank for repository root
- GitHub Personal Access Token if the repository is private

Do not paste the token into logs or chat. Enter it directly in the app.

## Basic Acceptance Test

- Launch the app and bind the repository.
- Confirm the workspace shows folders and Markdown files only.
- Open a Markdown file with valid frontmatter.
- Confirm the reader shows `project_code / doc_id@version`.
- Write a local note and leave the reader.
- Reopen the same document version and confirm the note returns.
- Test a document with missing frontmatter; it should remain readable but show that version notes are unavailable.

## Version-Scoped Note Test

- Open a document whose frontmatter is `doc_id=NAR-001` and `version=0.1.2`.
- Write a note.
- Change the repository document version to `0.1.3`, refresh, and reopen the same path.
- Confirm the `0.1.2` note is hidden and the `0.1.3` note starts blank.
- Change the document version back to `0.1.2`, refresh, and reopen it.
- Confirm the original `0.1.2` note is visible again.
