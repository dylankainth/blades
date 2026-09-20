/**
 * Strips people's names out of model-written text that is shown before both
 * sides have approved a match (the teaser, the push body, the Home feed).
 *
 * The prompts already ask the model not to name anyone; this is the
 * backstop for when it does anyway.
 */

export interface NameReplacement {
  /** Replaces "Sam". */
  plain: string;
  /** Replaces "Sam's". */
  possessive: string;
}

/** For a sentence addressed to both people at once. */
export const AS_ONE_OF_YOU: NameReplacement = {
  plain: "one of you",
  possessive: "one of your",
};

/** For a profile summary read by the other person. */
export const AS_THIS_PERSON: NameReplacement = {
  plain: "this person",
  possessive: "this person's",
};

const MIN_NAME_PART_LENGTH = 3;

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Full names plus each part of them, longest first so "Sam Lee" wins over "Sam". */
function nameTokens(names: ReadonlyArray<string | null | undefined>): string[] {
  const tokens = new Set<string>();
  for (const name of names) {
    const full = name?.trim();
    if (!full) continue;
    for (const candidate of [full, ...full.split(/\s+/)]) {
      // Shorter than this and the "name" is also an everyday word ("An", "Al").
      if (candidate.length >= MIN_NAME_PART_LENGTH) tokens.add(candidate);
    }
  }
  return [...tokens].sort((a, b) => b.length - a.length);
}

function capitalise(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export function redactNames(
  text: string,
  names: ReadonlyArray<string | null | undefined>,
  replacement: NameReplacement,
): string {
  const tokens = nameTokens(names);
  if (!text || tokens.length === 0) return text;

  const pattern = new RegExp(
    `(?<![\\p{L}\\p{N}])(?:${tokens.map(escapeRegExp).join("|")})(?![\\p{L}\\p{N}])(['’]s)?`,
    "giu",
  );
  return text.replace(pattern, (_match, possessive: string | undefined, offset: number) => {
    const swapped = possessive ? replacement.possessive : replacement.plain;
    const startsSentence = offset === 0 || /[.!?]\s+$/.test(text.slice(0, offset));
    return startsSentence ? capitalise(swapped) : swapped;
  });
}
