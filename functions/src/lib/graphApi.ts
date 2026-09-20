/**
 * Minimal Facebook Graph API helper for Tier B social context import (see
 * CLAUDE.md's two-tier context model / importSocialContext.ts).
 *
 * IMPORTANT: this only succeeds for accounts that have a role
 * (Admin/Developer/Tester) on the Meta App while it's in Development
 * Mode — `user_posts` is a Standard Access permission, so Graph API will
 * reject the request with an OAuth permission error for any other
 * account. That's an expected, not exceptional, outcome for the vast
 * majority of real users — see GraphApiError and how
 * importSocialContext.ts handles it.
 *
 * (Instagram used to have an equivalent helper here via Business Login +
 * `instagram_business_basic`, but that path required a Professional
 * Instagram account on top of the tester-role restriction. It's been
 * replaced with a plain public web search — see lib/parallel.ts.)
 */

const GRAPH_API_BASE = "https://graph.facebook.com/v21.0";

interface GraphApiErrorBody {
  message: string;
  type?: string;
  code?: number;
}

export class GraphApiError extends Error {
  constructor(
    message: string,
    public readonly graphError?: GraphApiErrorBody,
  ) {
    super(message);
    this.name = "GraphApiError";
  }
}

interface FacebookPostsResponse {
  data?: { message?: string; story?: string }[];
  error?: GraphApiErrorBody;
}

/**
 * Pulls recent Facebook timeline post text via `GET /me/posts`, using a
 * user access token that was granted the `user_posts` permission (classic
 * Facebook Login). Returns the non-empty post/story text of up to 25 most
 * recent posts.
 */
export async function fetchFacebookPostText(
  accessToken: string,
): Promise<string[]> {
  const url =
    `${GRAPH_API_BASE}/me/posts?fields=message,story&limit=25` +
    `&access_token=${encodeURIComponent(accessToken)}`;
  const res = await fetch(url);
  const body = (await res.json()) as FacebookPostsResponse;
  if (body.error) {
    throw new GraphApiError(body.error.message, body.error);
  }
  return (body.data ?? [])
    .map((post) => post.message ?? post.story ?? "")
    .filter((text) => text.trim().length > 0);
}
