# AI request test data

The request bodies each provider sent before `RELEASEFLOW_AI_TEMPERATURE` existed (ADR-0038),
used by `AiDefaultRequestBodyTest` to hold a deployment that does not set it to exactly
those bytes, so recorded requests still match. These files are captured, never edited by
hand.

| File | What it is |
| --- | --- |
| `default-request-openai.json` | The OpenAI Chat Completions body |
| `default-request-deepseek.json` | The DeepSeek Chat Completions body |
| `default-request-anthropic.json` | The Anthropic Messages body |

Source: commit `fce44db`, each provider built by `AiClassifierConfiguration.create` with the
API key `test-key`, the model `<provider>-test-model`, and a stub's base URL, classifying
`OpenAiCompatibleClassifierTest.request(null, null, "en")`, as `AiDefaultRequestBodyTest`
does now. A deliberate change to the request, such as a new instruction, captures them again.
