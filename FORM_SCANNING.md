# Form scanning

`POST /api/form-scans` accepts a logged-in user's JPEG, PNG, or WebP image up
to 10 MB. The backend writes it to a temporary file, submits it to the
configured local Ollama vision model for OCR, then deletes the file in all
cases. The image itself is never stored in PostgreSQL.

The resulting `FormScan` row is scoped to the authenticated user and stores
the OCR text, structured draft, form type, confidence, and review status.
`POST /api/form-scans/{scanId}/approve` only accepts the same owner's selected,
edited todos and events. It reuses the existing local persistence and Google
Tasks/Calendar integration. Nothing is created during OCR or draft generation.

## Vision-model configuration

The backend reads these environment variables only; no API key is exposed to
the browser:

```text
FORM_SCAN_VISION_BASE_URL=http://ollama-svc:11434
FORM_SCAN_VISION_MODEL=moondream
```

Pull the configured model on the host that owns Ollama. For the Raspberry Pi's
existing Ollama deployment this is normally:

```bash
ollama pull moondream
```

For lower-quality photos or handwritten fields, the response includes warnings
and defaults to a review state. Users must verify all dates and times before
approval.
