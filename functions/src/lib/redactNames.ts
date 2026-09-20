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

/** For a twin talking about its own person in the negotiation transcript. */
export const AS_MY_PERSON: NameReplacement = {
  plain: "my person",
  possessive: "my person's",
};

/** For a twin talking about the other twin's person. */
export const AS_YOUR_PERSON: NameReplacement = {
  plain: "your person",
  possessive: "your person's",
};

/**
 * Names that are also everyday English words. Matching these (and any name
 * of one or two letters) the same way as "Priya" would rewrite "you will
 * both" for someone called Will, so they only count as a name when the text
 * capitalises them. Not exhaustive; a name missing from here is still
 * redacted, it just also catches the lowercase word.
 */
const COMMON_WORD_NAMES: ReadonlySet<string> = new Set([
  "will", "may", "mark", "grace", "hope", "joy", "faith", "art", "bill", "rob",
  "pat", "sue", "dawn", "june", "april", "august", "summer", "autumn", "rose",
  "lily", "ivy", "iris", "olive", "hazel", "holly", "ruby", "pearl", "jade",
  "amber", "crystal", "sandy", "sky", "star", "chase", "hunter", "mason", "max",
  "guy", "rich", "frank", "dean", "penny", "jack", "don", "ray", "robin", "reed",
  "wade", "drew", "miles", "cliff", "dale", "glen", "heath", "lance", "pierce",
  "earl", "duke", "king", "prince", "major", "christian", "angel", "destiny",
  "harmony", "melody", "victor", "norm", "gene", "carol", "bud", "chip", "skip",
  "buck", "colt", "page", "paige", "sage", "mercy", "patience", "young", "long",
  "bright", "rain", "river", "ocean", "london", "paris", "chance", "justice",
]);

/**
 * Function words that open a sentence far more often than they name someone.
 * A capital letter proves nothing at the start of a sentence, so there these
 * are left alone unless a possessive follows ("An's project").
 */
const SENTENCE_OPENERS: ReadonlySet<string> = new Set([
  "an", "so", "do", "to", "my", "he", "me", "we", "in", "on", "no", "go", "as",
  "at", "by", "if", "is", "it", "or", "us",
]);

/**
 * "Will" and "May" open questions ("Will you two...", "May I suggest...") as
 * well as naming people ("Will builds robots."). At the start of a sentence
 * they are read as the verb only when one of these follows. When in doubt the
 * word is redacted: a garbled sentence is cheaper than a leaked name.
 */
const MODAL_NAMES: ReadonlySet<string> = new Set(["will", "may"]);
const AFTER_A_MODAL = /^\s+(you|we|they|i|it|this|that|these|those|the|there|both|either|each|one|someone|anyone)\b/i;

/** Below this a name is treated like a common word: capitalised matches only. */
const SHORT_NAME_LENGTH = 3;

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
      // A lone letter ("J" from "J Smith") is an initial, not a name.
      if (candidate.length >= 2) tokens.add(candidate);
    }
  }
  return [...tokens].sort((a, b) => b.length - a.length);
}

function isAmbiguous(token: string): boolean {
  return token.length < SHORT_NAME_LENGTH || COMMON_WORD_NAMES.has(token.toLowerCase());
}

/** "Will" or "WILL", but not "will". */
function isWrittenAsName(matched: string): boolean {
  const first = matched.charAt(0);
  return first !== first.toLowerCase();
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
    `(?<![\\p{L}\\p{N}])(${tokens.map(escapeRegExp).join("|")})(?![\\p{L}\\p{N}])(['’]s)?`,
    "giu",
  );
  return text.replace(
    pattern,
    (match: string, name: string, possessive: string | undefined, offset: number) => {
      const startsSentence = offset === 0 || /[.!?]\s+$/.test(text.slice(0, offset));
      if (isAmbiguous(name)) {
        if (!isWrittenAsName(name)) return match;
        if (startsSentence && !possessive) {
          const word = name.toLowerCase();
          const rest = text.slice(offset + match.length);
          if (SENTENCE_OPENERS.has(word)) return match;
          if (MODAL_NAMES.has(word) && AFTER_A_MODAL.test(rest)) return match;
        }
      }
      const swapped = possessive ? replacement.possessive : replacement.plain;
      return startsSentence ? capitalise(swapped) : swapped;
    },
  );
}
