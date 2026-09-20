/**
 * Minimal client for the Parallel AI Search API
 * (https://api.parallel.ai/v1/search).
 *
 * Used by importSocialContext.ts's Instagram provider. This replaced an
 * earlier Instagram Business Login (Graph API) branch that only worked for
 * Professional accounts with a tester/role on the Meta App — see CLAUDE.md.
 * Instead of any OAuth flow at all, the client just sends the twin's own
 * Instagram handle, and this does a public web search for it. Whatever's
 * publicly indexed (bio mentions, post excerpts, press/blog mentions, etc.)
 * gets folded into the same extractProfile() pipeline as the text dump.
 *
 * This works for literally any handle — no App Review, no tester role, no
 * Professional-account requirement — at the cost of only surfacing whatever
 * a web search engine has actually indexed about that handle.
 */

const PARALLEL_SEARCH_URL = "https://api.parallel.ai/v1/search";

interface ParallelSearchResult {
  url: string;
  title?: string | null;
  publish_date?: string | null;
  excerpts?: string[] | null;
}

interface ParallelSearchResponse {
  search_id: string;
  results: ParallelSearchResult[];
  warnings?: { message: string }[] | null;
}

interface ParallelErrorResponse {
  type: "error";
  error: { message: string; ref_id?: string };
}

export class ParallelApiError extends Error {}

/**
 * Runs a Parallel web search and flattens each result into a single
 * "<title> (<url>)\n<excerpts...>" chunk of text, ready to fold into
 * `socialContext`. Uses `mode: "basic"` — fast enough for a request/response
 * callable, higher quality than `turbo`, and doesn't need `advanced`'s
 * agentic multi-step search for a single-handle lookup like this.
 */
export async function searchWeb(
  apiKey: string,
  objective: string,
  searchQueries: string[],
): Promise<string[]> {
  const res = await fetch(PARALLEL_SEARCH_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-api-key": apiKey,
    },
    body: JSON.stringify({
      objective,
      search_queries: searchQueries,
      mode: "basic",
    }),
  });

  const body = (await res.json()) as ParallelSearchResponse | ParallelErrorResponse;
  if (!res.ok || (body as ParallelErrorResponse).type === "error") {
    const err = body as ParallelErrorResponse;
    throw new ParallelApiError(
      err.error?.message ?? `Parallel search failed with status ${res.status}.`,
    );
  }

  const success = body as ParallelSearchResponse;
  return (success.results ?? [])
    .filter((result) => (result.excerpts ?? []).length > 0)
    .map((result) => {
      const heading = result.title ? `${result.title} (${result.url})` : result.url;
      return `${heading}\n${(result.excerpts ?? []).join("\n")}`;
    });
}
