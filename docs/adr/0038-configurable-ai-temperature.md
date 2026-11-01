# ADR-0038: A configurable AI temperature

- Status: Accepted
- Date: 2026-10-31
- Amends: [ADR-0035](0035-evidence-bound-notes-and-audience-relevance.md), whose
  temperature was fixed rather than configurable

## Context

ADR-0035 asks every classification request for temperature `0`, "fixed rather than
configurable", and accepts that a model taking only its default temperature can no longer
classify. The release that carries it, 1.1.0, is a minor one. Before it, no request named a
temperature, so a deployment using such a model worked; after the upgrade every one of its
changes would become a `CLASSIFIER_FALLBACK`. A minor release must not do that.

Such models exist for two of the three providers:

- OpenAI's reasoning models accept only their default temperature.
- Anthropic's API reference marks `temperature` deprecated: "Models released after Claude
  Opus 4.6 do not support setting temperature. A value of 1.0 will be accepted for
  backwards compatibility, all other values will be rejected with a 400 error."

The ranges each API accepts are, from its reference: OpenAI `0` to `2`, DeepSeek `0` to `2`,
and Anthropic `0.0` to `1.0`.

## Decision

- `RELEASEFLOW_AI_TEMPERATURE` sets the temperature of every classification request. It
  stays one value for every request of a deployment, as ADR-0035 intended; it is never
  chosen per change, per Project, or per request.
- Unset or blank means `0`. A deployment that does not set it sends exactly the bytes it
  sent before this decision, so recorded requests still match. The Compose files forward
  `${RELEASEFLOW_AI_TEMPERATURE:-0}`, because an empty value is what Compose passes for an
  unset variable written `${…:-}`.
- `default` leaves the temperature out of the request, so the model uses its own, as every
  request did before ADR-0035.
- Any other value must parse as a number within the selected provider's range, inclusive.
  Otherwise startup fails, like the other AI settings, with a message naming the variable
  and the accepted range. Without a provider, nothing is sent and the value is not read.
- ADR-0035's reasons for `0` stand: it remains the default, and the reason to change it is
  a model that cannot take it, not variety of wording.

## Consequences

- A deployment using an OpenAI reasoning model, or a Claude model released after Opus 4.6,
  keeps classifying after the upgrade once it sets `RELEASEFLOW_AI_TEMPERATURE=default`. The
  CHANGELOG and the upgrade note say so before the upgrade, since nothing can detect such a
  model at startup.
- A deployment that sets nothing behaves as ADR-0035 describes, request for request.
- A value other than `0` widens the variance ADR-0035 narrowed. That is the operator's
  choice and applies to every change alike.
