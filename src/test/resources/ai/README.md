# AI request test data

The request bodies `AiDefaultRequestBodyTest` holds each provider to, so recorded requests
still match: for OpenAI and DeepSeek, the bodies sent before `RELEASEFLOW_AI_TEMPERATURE`
existed (ADR-0038); for Anthropic, the body sent since API version 2026-10-21, which takes no
temperature (ADR-0045). These files are captured, never edited by hand.

| File | What it is |
| --- | --- |
| `default-request-openai.json` | The OpenAI Chat Completions body |
| `default-request-deepseek.json` | The DeepSeek Chat Completions body |
| `default-request-anthropic.json` | The Anthropic Messages body |

Source: each provider built by `AiClassifierConfiguration.create` with the API key
`test-key`, the model `<provider>-test-model`, and a stub's base URL, classifying
`OpenAiCompatibleClassifierTest.request(null, null, "en")`, as `AiDefaultRequestBodyTest`
does now. The OpenAI and DeepSeek bodies were captured at commit `fce44db`. The Anthropic body
was captured with anthropic-java 3.8.0 on the change that moved it to API version 2026-10-21;
it is the earlier capture without its last field, `"temperature":0.0`. A deliberate change to
the request, such as a new instruction, captures them again.
