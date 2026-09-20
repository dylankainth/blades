/**
 * Thin client for the Meta Model API ("Muse Spark").
 *
 * Confirmed from Meta's own docs: Model API speaks the Anthropic Messages
 * API shape natively. Point the official `@anthropic-ai/sdk` at
 * `https://api.meta.ai` (the SDK appends `/v1/messages`) with the Model API
 * key as the bearer token — no custom fetch/parsing needed.
 */
import Anthropic from "@anthropic-ai/sdk";

export const META_MODEL_BASE_URL = "https://api.meta.ai";
export const MUSE_SPARK_MODEL = "muse-spark-1.3";

export interface MetaModelMessage {
  role: "user" | "assistant";
  content: string;
}

export interface CallMetaModelOptions {
  apiKey: string;
  system: string;
  messages: MetaModelMessage[];
  maxTokens?: number;
  /**
   * How hard Muse Spark thinks before answering. Measured on a short chat
   * turn (2026-09-19): default ~9.0 s / 563 thinking tokens, "low" ~6.3 s /
   * 289. The API rejects "minimal" and rejects disabling thinking outright.
   */
  effort?: "low" | "medium" | "high";
}

/**
 * Calls Muse Spark via the Messages API and returns the assistant's text
 * reply. Throws on API errors or an empty text response.
 */
export async function callMetaModel(
  options: CallMetaModelOptions,
): Promise<string> {
  // Muse Spark 1.3 defaults into extended reasoning ("thinking") and counts
  // that against max_tokens — a short reply can easily burn the whole
  // budget on thinking_tokens and leave zero for visible output (observed:
  // 1021/1024 tokens spent thinking, empty `content`, stop_reason
  // "max_tokens"). Default high enough to leave real headroom after
  // thinking, until Meta's docs confirm a way to cap/disable reasoning
  // effort explicitly.
  const { apiKey, system, messages, maxTokens = 4096, effort = "low" } = options;

  const client = new Anthropic({
    baseURL: META_MODEL_BASE_URL,
    authToken: apiKey,
  });

  const response = await client.messages.create({
    model: MUSE_SPARK_MODEL,
    max_tokens: maxTokens,
    system,
    messages: messages.map((m) => ({ role: m.role, content: m.content })),
    // Not in this SDK version's types yet; the Meta endpoint accepts it.
    ...({ output_config: { effort } } as Record<string, unknown>),
  });

  const textBlock = response.content.find(
    (block): block is Anthropic.TextBlock => block.type === "text",
  );

  if (!textBlock?.text) {
    throw new Error(
      `Muse Spark returned no text content: ${JSON.stringify(response)}`,
    );
  }

  return textBlock.text;
}
