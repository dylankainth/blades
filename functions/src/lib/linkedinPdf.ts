/**
 * LinkedIn PDF text extraction for Tier B social context import (see
 * CLAUDE.md's two-tier context model / importSocialContext.ts).
 *
 * LinkedIn has no equivalent of a "public profile web search" the way
 * lib/parallel.ts covers Instagram, and its own API is invite-only partner
 * access — not a weekend build. Instead this rides on a feature LinkedIn
 * ships natively: "Save to PDF" on your own profile. The client uploads
 * that PDF (base64, over the same callable as the other providers) and this
 * extracts its raw text with pdf-parse. No OAuth, no App Review, works for
 * anyone with a LinkedIn account.
 */
import pdfParse from "pdf-parse";

export class LinkedinPdfError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "LinkedinPdfError";
  }
}

// Comfortably above a real LinkedIn "Save to PDF" export (typically a few
// hundred KB), while staying well under an onCall request's ~10MB body cap
// once base64 overhead (~37%) is factored in.
const MAX_PDF_BYTES = 5 * 1024 * 1024;

/** Extracts plain text from a base64-encoded PDF. Throws LinkedinPdfError on anything unreadable. */
export async function extractLinkedinPdfText(pdfBase64: string): Promise<string> {
  const cleaned = pdfBase64.replace(/^data:application\/pdf;base64,/, "");

  let buffer: Buffer;
  try {
    buffer = Buffer.from(cleaned, "base64");
  } catch {
    throw new LinkedinPdfError("That didn't look like a valid PDF file.");
  }
  if (buffer.length === 0) {
    throw new LinkedinPdfError("The uploaded PDF was empty.");
  }
  if (buffer.length > MAX_PDF_BYTES) {
    throw new LinkedinPdfError(
      "That PDF is too large (5MB max) — LinkedIn's own \"Save to PDF\" export should be well under that.",
    );
  }

  let text: string;
  try {
    const parsed = await pdfParse(buffer);
    text = parsed.text ?? "";
  } catch (err) {
    throw new LinkedinPdfError(
      `Couldn't read that PDF: ${err instanceof Error ? err.message : String(err)}`,
    );
  }

  const cleanedText = text.replace(/[ \t]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").trim();
  if (!cleanedText) {
    throw new LinkedinPdfError("Couldn't find any readable text in that PDF.");
  }
  return cleanedText;
}
