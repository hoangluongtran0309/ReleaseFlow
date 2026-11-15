# ADR-0045: Anthropic API version 2026-10-21, with no temperature

- Status: Accepted
- Date: 2026-11-15
- Amends: [ADR-0038](0038-configurable-ai-temperature.md), whose temperature reaches every
  provider, and [ADR-0039](0039-ai-temperature-fallback.md), whose fallback recognises
  Anthropic's refusal
- Refers to: [ADR-0009](0009-automatic-ai-classification.md), for one automatic request per
  change, and [ADR-0035](0035-evidence-bound-notes-and-audience-relevance.md), for why the
  temperature is `0`

## Context

ReleaseFlow calls Claude through `com.anthropic:anthropic-java` 2.73.0, which sends
`anthropic-version: 2023-06-01`. Version 3.0.0 moved to API version `2026-10-21`, and 3.8.0
is the latest release. Dependabot holds the 3.x majors back until this decision (#61).

Anthropic's migration guide for the new version
(<https://platform.claude.com/docs/en/api/migration-2026-10-21>) says, under "Sampling
parameters are removed":

> In `2026-10-21`, a request that sends `temperature`, `top_p`, or `top_k` returns a 400
> error, even at the parameter's default value.
>
> Remove the three parameters from your requests.
>
> As of the latest major versions, the SDK and the CLI no longer have the three parameters.
>
> Without these parameters, a request samples with their defaults, including a
> `temperature` of 1.0, the same as a `2023-06-01` request that leaves them out. Models that
> already accept only the defaults, such as Claude 4.7 and later models, sample as before. On
> models that accept other values, such as Claude Sonnet 4.6, a request that lowered
> `temperature` for more consistent output loses that effect. No other setting replaces it.
> If a request relies on a non-default value, either stop sending the parameter, or keep
> that request on `2023-06-01`.

and, under "API keys are sent as `Authorization: Bearer`":

> The SDK and the CLI send your API key as `Authorization: Bearer <key>` instead of in the
> `X-Api-Key` header, so change any proxy or gateway that reads or sets `X-Api-Key` to use
> `Authorization` […]. Also change code in your own program that reads `X-Api-Key`, such as
> middleware, a log filter, or a test stub. The Claude API accepts a key in either header.

The guide's only platform limit is Amazon Bedrock, where `2026-10-21` "is available only
for Claude 4.7 and later models". ReleaseFlow has no Bedrock or Vertex client: it sends an
API key to `RELEASEFLOW_ANTHROPIC_BASE_URL`, `https://api.anthropic.com` by default.

Building develop against 3.8.0 in a scratch worktree, with nothing sent to Anthropic, shows
that the only code that stops compiling is `builder.temperature(...)` in
`AnthropicChangeClassifier`. None of the guide's other SDK changes reaches ReleaseFlow: it
builds its request with `system(String)`, `addUserMessage(String)`, and `outputConfig`,
which keep their names, and it uses no `MessageParam` builder, one-value enum, Text
Completions, Models method, second credential, `AnthropicBackend` getter, or interceptor.

ADR-0035 chose temperature `0` so that a change's answer is as stable as the model allows;
ADR-0038 made it configurable; ADR-0039 sends a request once more without it when the model
refuses it. On `2026-10-21` every request that names a temperature is refused, so ADR-0039's
fallback would fire on the first request after every start, on every Claude model.

## Decision

### Temperature

- ReleaseFlow sends Anthropic no `temperature`, `top_p`, or `top_k`, on any request and for
  any value of `RELEASEFLOW_AI_TEMPERATURE`. Claude samples with its defaults.
- `RELEASEFLOW_AI_TEMPERATURE` is still read and checked as ADR-0038 says when the provider is
  `anthropic`: unset, blank, `default`, or a number from `0` to `1`; anything else stops
  startup with the same message. A setting that starts 1.2 therefore starts 1.3, and the
  other way round, so nobody's upgrade or rollback stops on it. The value decides nothing for
  Anthropic.
- A number other than `0` logs one warning at startup, saying that Anthropic API version
  2026-10-21 takes no temperature and that the value is ignored. `0`, blank, unset, and
  `default` log nothing: `0` is what the Compose files forward for an unset variable, so it
  cannot tell an operator's choice from no choice, and asking for the default is what
  ReleaseFlow now does.
- ADR-0039 no longer applies to Anthropic. Nothing is sent that could be refused for its
  temperature, so the recognition of Anthropic's refusal (a 400 `invalid_request_error`
  whose message names `temperature`) is removed with its warning. Every 400 from Anthropic is
  a failure handled as ADR-0009 says, with no second request. ADR-0039 stands unchanged for
  OpenAI and DeepSeek.
- ADR-0038 stands unchanged for OpenAI and DeepSeek: one temperature for every request, `0`
  by default.

### API version

- ReleaseFlow follows the SDK's API version and sets none of its own: `2026-10-21` with
  anthropic-java 3.x. A stub test asserts the header, so an SDK release that moves the
  version fails the build and becomes its own decision rather than shipping.
- Rejected: keeping `2023-06-01` through a header override on 3.x to keep temperature `0`.
  The 3.x types describe `2026-10-21`, so the temperature would travel as an untyped extra
  body property against a version the SDK no longer models, and every deployment would stay
  on the old version to serve the models that still accept another temperature.
- Rejected: staying on 2.73.0. It keeps the old version only as long as Dependabot's ignore
  does, and the next decision would be this one.
- Rejected: sending the key in `x-api-key` with `putHeader` to spare gateways the change. The
  Claude API accepts both, and ReleaseFlow would carry a header the SDK no longer sends for a
  gateway nobody has reported; the upgrade note tells operators instead.

What an operator sees, by model, on the Claude API:

| Model | Before (2.73.0, `2023-06-01`) | After (3.x, `2026-10-21`) |
| --- | --- | --- |
| Claude 4.7 and later | First request after each start refused for its temperature, then sent without it, with one warning (ADR-0039); sampled at the default | Every request sent once, no warning; sampled at the default, as before |
| Claude 4.6 and earlier (such as Sonnet 4.6, Opus 4.6, Haiku 4.5) | Sampled at `0`, or at the configured temperature | Sampled at the default `1.0`, so classifications, and the notes written from them, vary more between runs than on 1.2: a change classified again may get different wording or, where the evidence is thin, a different category |

Nothing else differs. Rules still override the AI, a breaking, security, Unknown, triggered,
or AI-flagged change still needs a person's review, and a change still gets exactly one
automatic request. An operator who needs temperature `0` can choose OpenAI or DeepSeek.

### What changes in the request

Measured with `AiDefaultRequestBodyTest`'s request (key `test-key`, model
`anthropic-test-model`, the stub's base URL), built by 2.73.0 on develop and by 3.8.0 with
`builder.temperature(...)` removed.

The body loses its last field and nothing else. The pinned body
`src/test/resources/ai/default-request-anthropic.json` (9,748 bytes) ends

```text
…never translate them.\n","temperature":0.0}
```

and the 3.8.0 body (9,730 bytes) ends

```text
…never translate them.\n"}
```

Removing those 18 bytes, `,"temperature":0.0`, from the pinned body gives the 3.8.0 body byte
for byte. It is also the body that `RELEASEFLOW_AI_TEMPERATURE=default` sent before, so for a
deployment on `default` the body does not change at all.

The headers, apart from `host` and `content-length` (which follows the body):

```diff
 accept: application/json
 accept-encoding: gzip
-anthropic-version: 2023-06-01
+anthropic-version: 2026-10-21
+authorization: Bearer test-key
 connection: Keep-Alive
 content-type: application/json
-user-agent: AnthropicClientImpl/Java 2.73.0
-x-api-key: test-key
+user-agent: AnthropicClientImpl/Java 3.8.0
 x-stainless-arch: x64
 x-stainless-kotlin-version: 2.3.21
 x-stainless-lang: java
 x-stainless-os: Linux
 x-stainless-os-version: …
-x-stainless-package-version: 2.73.0
+x-stainless-package-version: 3.8.0
 x-stainless-read-timeout: 5
 x-stainless-retry-count: 0
 x-stainless-runtime: JRE
 x-stainless-runtime-version: …
 x-stainless-timeout: 5
```

The `user-agent` and `x-stainless-package-version` name the SDK release, as they do after any
update. The path stays `POST /v1/messages`.

OpenAI and DeepSeek do not use anthropic-java. Against 3.8.0, `AiDefaultRequestBodyTest`'s
six OpenAI and DeepSeek cases pass unchanged against their pinned bodies, and nothing in
their requests changes.

The pinned Anthropic body is captured again from the 3.x build, as
`src/test/resources/ai/README.md` asks for a deliberate change to a request, and the README
names the new source. It is never edited by hand.

### Verification

- `AiDefaultRequestBodyTest`: OpenAI and DeepSeek unchanged; Anthropic against the new
  capture, and, for Anthropic only, every value of `RELEASEFLOW_AI_TEMPERATURE` (unset, blank,
  `0`, `0.5`, `1`, `default`) sends that same body.
- `AnthropicStub` records `Authorization` and `x-api-key` separately. A stub test asserts
  `Authorization: Bearer <key>`, no `x-api-key`, `anthropic-version: 2026-10-21`, and a body
  with no `temperature`, `top_p`, or `top_k`.
- The Anthropic cases of ADR-0039's tests become their opposite: a 400 whose message names
  `temperature` is one request and an ordinary failure, with no warning. The OpenAI and
  DeepSeek cases stay as they are.
- Startup: a number other than `0` logs the warning once; `0`, blank, unset, and `default`
  log nothing; a value out of range still stops startup.
- `GoldenReleaseNotesIntegrationTest` runs with AI off and is unaffected; it must keep
  passing. The browser suite runs with every AI and DeepL setting blank.

The stubs prove what ReleaseFlow sends; they cannot prove that Anthropic accepts it, and the
dbvault replay runs on OpenAI with no Anthropic recordings. One live check would close that:

- exactly two classification requests, sent by the final build's `AnthropicChangeClassifier`
  after `./mvnw verify` passes, with `AiDefaultRequestBodyTest`'s request (about 2,400 input
  tokens, `max_tokens` 16,000 as in production): one to `claude-haiku-5-5`, a model that took
  only the default before, and one to `claude-haiku-4-5`, one that took `0`;
- nothing is retried: the client's retries are off, and no Anthropic answer earns a second
  request;
- each passes when Anthropic answers 200 with `end_turn` and the answer parses as a
  classification; only the HTTP status, the stop reason, and the category are printed;
- at list prices, about $0.0003 and $0.0025 of input, and at most $0.008 and $0.08 of output
  if a model used all 16,000 tokens: under $0.10 for both in the worst case.

The maintainer supplies a key for these two calls only, in a file outside the repository
that is read for them and never printed, then deleted and the key revoked. The pull request
reports the outcome.

### Upgrade note

The CHANGELOG and `docs/configuration.md` say, for a deployment with
`RELEASEFLOW_AI_PROVIDER=anthropic`:

- Claude requests use API version `2026-10-21` and carry no temperature.
  `RELEASEFLOW_AI_TEMPERATURE` no longer affects them; a number other than `0` logs a warning
  at startup and can be removed. No setting needs to change.
- On Claude 4.7 and later, classification samples as before, and the first request after each
  start is no longer refused, so its warning goes away.
- On Claude 4.6 and earlier (such as Sonnet 4.6, Opus 4.6, or Haiku 4.5), classification
  runs at the model's default temperature, `1.0`, instead of `0`, so notes vary more between
  runs than on 1.2. OpenAI or DeepSeek at `0` keep the temperature; a Claude 4.7 or later
  model takes only the default anyway.
- The API key is sent as `Authorization: Bearer <key>` instead of `x-api-key`. A proxy or
  gateway at `RELEASEFLOW_ANTHROPIC_BASE_URL` that reads or sets `x-api-key`, or that checks
  `anthropic-version`, must accept the new headers before the upgrade; one that forwards to
  Amazon Bedrock accepts `2026-10-21` only for Claude 4.7 and later. The Claude API itself
  accepts both.
- anthropic-java 3.x is licensed under Apache-2.0 instead of MIT. Its jars carry no `NOTICE`
  file, so section 4(d) of the licence asks nothing more of ReleaseFlow, and `NOTICE` needs no
  change (below).

### Licence

anthropic-java 3.8.0's three jars (`anthropic-java`, `anthropic-java-core`,
`anthropic-java-client-okhttp`) each carry `META-INF/licenses/<artifact>/LICENSE`, the Apache
License 2.0, and `LICENSE-MIT`, which says the MIT License still applies to the versions
published under it and to contributions merged before 3.0.0. None carries a `NOTICE` file,
and the SDK's repository has none at `v3.8.0`. ReleaseFlow's JAR keeps each dependency jar
whole under `BOOT-INF/lib`, and the image copies that JAR unchanged, so both licence files
reach every copy. ReleaseFlow's `NOTICE` already points at the licences inside its
dependencies and names Apache-2.0 and MIT among them; it does not change. A later release
that adds a `NOTICE` would need its own look.

### Dependabot

The same change removes #61's ignore of anthropic-java's semver-major updates from
`.github/dependabot.yml`, so later majors arrive as pull requests. One that moves the API
version or the headers fails the stub test.

## Consequences

- Every Anthropic deployment keeps classifying after the upgrade with no action, on every
  Claude model the Claude API serves.
- Claude 4.6 and earlier lose ADR-0035's temperature `0`. That variance cannot be configured
  away on Anthropic, only avoided by another model or provider. The rules and mandatory review
  that keep an unstable answer from settling a change are unaffected.
- `RELEASEFLOW_AI_TEMPERATURE` now means something for two providers, not three; the
  configuration guide says so.
- ADR-0039 covers OpenAI and DeepSeek only, and the one way Anthropic could be asked twice
  for a change is gone.
- A gateway that only reads `x-api-key` stops authenticating until it is changed; the upgrade
  note says so first.
