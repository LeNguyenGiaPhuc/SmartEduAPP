# SmartEduAPP source structure

```text
app/src/main/java/hcmute/com/smarteduapp/
|-- data/
|   |-- local/
|   |   |-- entity/       Room tables and their relationships
|   |   |-- dao/          SQLite CRUD queries for each table
|   |   `-- database/     Room database configuration and singleton
|   `-- repository/       Background-thread data access exposed to UI
|-- domain/
|   `-- usecase/
|       |-- subject/      Subject business rules
|       |-- document/     Document and OCR-text business rules
|       `-- study/        Summary, questions, quiz and history rules
|-- service/
|   |-- image/            Camera, gallery and image URI integration
|   |-- document/         Document text scanning for images, text files and PDFs
|   |-- ocr/              Google ML Kit text recognition
|   |-- study/            Quiz parsing and scoring services
|   `-- ai/               AI API, prompts and JSON DTOs
`-- ui/
    |-- common/           Reusable UI helpers
    |-- main/             MainActivity and screen navigation
    |-- subject/          Subject screens
    |-- document/         Document and OCR screens
    `-- study/            Summary, question, quiz and history screens
```

## Module responsibilities

- `data/local/entity`: Database models only. These classes must not contain UI code.
- `data/local/dao`: SQL/Room operations only. UI code must not call a DAO directly.
- `data/local/database`: Creates `smartedu.db` and registers Room entities/DAOs.
- `data/repository`: The boundary between UI and Room. Database work runs off the main thread.
- `domain/usecase`: Business rules that coordinate repositories and services.
- `service/image`: Camera/gallery integration and image URI handling.
- `service/document`: Detects document attachment types and extracts text from images, text files and PDFs.
- `service/ocr`: OCR provider integration.
- `service/ai`: AI requests, prompts and structured JSON responses.
- `service/study`: AI quiz parsing and quiz scoring logic that should not live in UI code.
- `ui/common`: Shared view-building utilities.
- `ui/main`: Main navigation flow, home sidebar controller and home dashboard renderer.
- `ui/subject`: Subject/document list rendering.
- `ui/document`: Document attachment preview and thumbnail rendering.
- `ui/study`: Summary, question bank, quiz, result, history and AI chat rendering.

Android layout resources remain in `res/layout` because Android does not support nested
layout resource folders. Screen prefixes identify their feature:

- `screen_subject_*`: subject management
- `screen_document_*` and `screen_process_document`: document/OCR flow
- `screen_summary_*`: summary flow
- `screen_question_*`, `screen_questions`, and `screen_quiz_*`: question/quiz flow
- `screen_history`: learning history

New feature code must be added to these packages instead of placing ML Kit, API or
business logic directly inside `MainActivity`.

`MainActivity` is still the central screen coordinator for this MVP, but repeated UI card
rendering has been split into renderer/controller classes so each module is easier to read:

- `SubjectController`: manages subject list, subject form, subject detail and subject CRUD.
- `DocumentController`: manages document CRUD, attachment file flow, OCR and scanned-content screens.
- `StudyController`: manages AI summary, AI chat, question bank, quiz and quiz result flow.
- `HistoryController`: manages learning-history loading and rendering.
- `HomeMenuController`: opens/closes the sidebar menu.
- `HomeDashboardRenderer`: renders recent activity and recent quiz history on the home screen.
- `SubjectListRenderer`: renders subject cards and document cards.
- `DocumentAttachmentUi`: renders attachment thumbnails and document preview.
- `QuestionBankRenderer`: renders saved questions and small edit/delete actions.
- `QuizUiRenderer`: renders quiz questions and result review.
- `ChatMessageRenderer`: renders AI chat messages.
