# Form scanning

`POST /api/form-scans` accepts a logged-in user's JPEG, PNG, or WebP image up
to 10 MB. The backend writes it to a temporary file, runs local Tesseract OCR,
then deletes the file in all cases. The image itself is never stored in
PostgreSQL. The existing Ollama chat model receives only the extracted OCR text
to create the structured draft.

The resulting `FormScan` row is scoped to the authenticated user and stores
the OCR text, structured draft, form type, confidence, and review status.
`POST /api/form-scans/{scanId}/approve` only accepts the same owner's selected,
edited todos and events. It reuses the existing local persistence and Google
Tasks/Calendar integration. Nothing is created during OCR or draft generation.

## OCR configuration

The backend includes Tesseract plus Swedish and English language data. Its OCR
behaviour can be configured without exposing any API key to the browser:

```text
FORM_SCAN_OCR_COMMAND=tesseract
FORM_SCAN_OCR_LANGUAGES=swe+eng
FORM_SCAN_OCR_PSM=6
FORM_SCAN_OCR_TIMEOUT_SECONDS=60
```

For lower-quality photos or handwritten fields, the response includes warnings
and defaults to a review state. Users must verify all dates and times before
approval.
