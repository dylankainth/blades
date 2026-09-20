/**
 * pdf-parse ships no types of its own. A minimal ambient declaration is
 * enough here — lib/linkedinPdf.ts only ever reads `.text` off the result.
 */
declare module "pdf-parse" {
  interface PdfParseResult {
    text: string;
    numpages?: number;
    numrender?: number;
    info?: Record<string, unknown>;
    metadata?: unknown;
    version?: string;
  }

  function pdfParse(
    dataBuffer: Buffer,
    options?: Record<string, unknown>,
  ): Promise<PdfParseResult>;

  export = pdfParse;
}
