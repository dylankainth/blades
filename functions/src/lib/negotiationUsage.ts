/**
 * Running token totals for one negotiation, pure helpers, no Firestore or
 * model calls in here. negotiateTwins.ts folds each model call's usage in
 * via callMetaModel's `onUsage` hook, then stamps the wall-clock time and
 * model settings on at the end to get the NegotiationUsage it persists.
 */
import type { MetaModelEffort, MetaModelUsage } from "./metaModel";
import type { NegotiationUsage } from "../types";

export interface UsageTotals {
  modelCalls: number;
  inputTokens: number;
  outputTokens: number;
}

export const EMPTY_USAGE_TOTALS: UsageTotals = {
  modelCalls: 0,
  inputTokens: 0,
  outputTokens: 0,
};

/** Returns new totals with one more model call folded in, never mutates. */
export function addCallUsage(
  totals: UsageTotals,
  call: MetaModelUsage,
): UsageTotals {
  return {
    modelCalls: totals.modelCalls + 1,
    inputTokens: totals.inputTokens + call.inputTokens,
    outputTokens: totals.outputTokens + call.outputTokens,
  };
}

export function toNegotiationUsage(
  totals: UsageTotals,
  run: { durationMs: number; model: string; effort: MetaModelEffort },
): NegotiationUsage {
  return {
    ...totals,
    totalTokens: totals.inputTokens + totals.outputTokens,
    durationMs: run.durationMs,
    model: run.model,
    effort: run.effort,
  };
}
