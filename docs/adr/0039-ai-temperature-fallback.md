# ADR-0039: Falling back when a model refuses the temperature

- Status: Accepted
- Date: 2026-11-01
- Amends: [ADR-0038](0038-configurable-ai-temperature.md), whose consequence "nothing can
  detect such a model" no longer holds
- Refers to: [ADR-0009](0009-automatic-ai-classification.md), for one automatic request per
  change

## Context

ADR-0038 keeps temperature `0` as the default and leaves a model that cannot take it to
`RELEASEFLOW_AI_TEMPERATURE=default`: "The CHANGELOG and the upgrade note say so before the
upgrade, since nothing can detect such a model at startup." Its own context says
a minor release must not turn every change of a working deployment into a
`CLASSIFIER_FALLBACK`.

That default now meets most current models. Claude Opus 4.7 and later, the 5.x models
included, reject a temperature other than their own with a 400, as ADR-0038 quotes, and
OpenAI's reasoning models do the same. Every deployment on one of them that upgrades without
reading the upgrade note fails every classification. A note is not a safeguard.

Startup still cannot tell such a model apart, since nothing is sent then. The first
classification request can: the provider says which parameter it refused.

- OpenAI reports an `invalid_request_error` whose `error.param` names the parameter at
  fault, as its error-codes guide shows for another parameter. For the temperature it was
  reported for o3-mini (openai/openai-python#2072) as
  `{"error":{"message":"Unsupported parameter: 'temperature' is not supported with this model.","type":"invalid_request_error","param":"temperature","code":"unsupported_parameter"}}`.
  DeepSeek serves the same API shape.
- Anthropic reports `{"type":"error","error":{"type":"invalid_request_error","message":"…"}}`
  and documents only that `temperature` "can … return a 400 error" on newer models, not the
  message; a refusal has been reported as "`temperature` is deprecated for this model."

## Decision

- A request is sent as before: with the configured temperature, `0` by default, so a
  deployment that sets nothing sends the bytes ADR-0038 holds it to.
- When the provider answers 400 and the error is about the temperature, the same request is
  sent once more without it, and every later request of the process leaves it out. One
  warning names the provider and the model and says that `RELEASEFLOW_AI_TEMPERATURE=default`
  avoids the first refusal.
- The refusal is recognised narrowly: for OpenAI and DeepSeek, a 400 whose `error.param` is
  `temperature`; for Anthropic, a 400 `invalid_request_error` whose message names
  `temperature`. Every other 400, and every other status, is handled exactly as before, with
  no second request. The error body is read only to decide this and is never stored or
  logged, so AI failures keep their fixed messages.
- `default` sends no temperature from the first request, as ADR-0038 says. An explicit number
  that the model refuses falls back the same way, with the same warning: the operator chose a
  value, but a model that takes none cannot be given one.
- What falls back is the process, not the deployment's setting. A restart sends the
  configured temperature again, and refuses once again before it falls back.
- ADR-0009's one automatic request per change stands for answers. A request refused for its
  temperature produced no answer, so the second one is the same request, not a retry of a
  failure; it is the only one, and when it fails, that failure is the change's, handled as
  ADR-0009 says.

## Consequences

- A deployment on a model that takes only its own temperature keeps classifying after the
  upgrade with no action. Its first request after each start is refused once, which costs a
  round trip and leaves a warning in the log.
- `RELEASEFLOW_AI_TEMPERATURE=default` is no longer needed to keep working; it only avoids
  that first refusal and its warning.
- A deployment whose model takes a temperature sends exactly what ADR-0038 describes.
- A provider that words its refusal differently than recognised here is handled as before
  ADR-0039: every change fails AI classification, and `default` is the remedy. The tests use
  the bodies above, so they cannot notice a provider rewording its refusal; the failures in
  the log can.
