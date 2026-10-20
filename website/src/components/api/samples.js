// Code samples for an operation, against the quickstart's address. Every value a reader
// must supply is a visible placeholder: a shell variable, a JavaScript or Java variable, or
// the example body built from the request's schema.

const BASE = 'http://localhost:8080';

// projectId is PROJECT_ID; a header keeps its own words: X-GitHub-Event is X_GITHUB_EVENT.
function envName(name) {
  return (/-/.test(name) ? name : name.replace(/([a-z0-9])([A-Z])/g, '$1_$2')).replace(/[^A-Za-z0-9]+/g, '_').toUpperCase();
}

function camelName(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+([a-z0-9])/g, (_, letter) => letter.toUpperCase());
}

function indent(text, spaces) {
  return text
    .split('\n')
    .map((line, index) => (index === 0 ? line : `${' '.repeat(spaces)}${line}`))
    .join('\n');
}

function shape(operation) {
  const webhook = operation.path.startsWith('/webhooks/');
  const write = operation.method !== 'GET';
  return {
    webhook,
    // Every /api call shares one session: GET /api/csrf starts it, and signing in keeps it.
    session: !webhook,
    signIn: !webhook && operation.role !== 'public',
    csrf: !webhook && write,
    headers: operation.parameters.filter((parameter) => parameter.in === 'header'),
    query: operation.parameters.filter((parameter) => parameter.in === 'query' && parameter.required),
    body: operation.body,
    json: operation.body && operation.body.schema.kind !== 'binary' ? JSON.stringify(operation.body.example, null, 2) : null,
  };
}

function queryString(parameters, value) {
  return parameters.length ? `?${parameters.map((parameter) => `${parameter.name}=${value(parameter.name)}`).join('&')}` : '';
}

export function curlSample(operation) {
  const {webhook, session, signIn, csrf, headers, query, body, json} = shape(operation);
  const comments = [];
  if (signIn) comments.push('# Sign in first and keep the session cookie in cookies.txt (see Sessions and CSRF).');
  if (csrf) comments.push('# CSRF_HEADER and CSRF_TOKEN are the headerName and token GET /api/csrf returned.');
  if (webhook) comments.push('# Sign the exact bytes of payload.json with the webhook secret (see the guide).');
  const url = `${BASE}${operation.path.replace(/\{(\w+)\}/g, (_, name) => `$${envName(name)}`)}${queryString(query, (name) => `$${envName(name)}`)}`;
  const lines = [`curl${operation.method === 'GET' ? '' : ` -X ${operation.method}`} "${url}"`];
  if (session) lines.push('  -b cookies.txt -c cookies.txt');
  if (csrf) lines.push('  -H "$CSRF_HEADER: $CSRF_TOKEN"');
  for (const header of headers) lines.push(`  -H "${header.name}: $${envName(header.name)}"`);
  if (body) {
    lines.push(`  -H "Content-Type: ${body.contentType}"`);
    lines.push(json ? `  --data '${indent(json, 2)}'` : '  --data-binary @payload.json');
  }
  return [...comments, lines.join(' \\\n')].join('\n');
}

export function javascriptSample(operation) {
  const {webhook, session, csrf, headers, query, body, json} = shape(operation);
  const comments = [];
  if (csrf) comments.push('// csrf is the JSON GET /api/csrf returned, in the same signed-in session.');
  if (webhook) comments.push('// payload is the exact body you signed with the webhook secret.');
  const url = `${BASE}${operation.path.replace(/\{(\w+)\}/g, (_, name) => `\${${name}}`)}${queryString(query, (name) => `\${${name}}`)}`;
  const options = [];
  if (operation.method !== 'GET') options.push(`method: '${operation.method}',`);
  if (session) options.push("credentials: 'include',");
  const headerLines = [];
  if (csrf) headerLines.push('[csrf.headerName]: csrf.token,');
  for (const header of headers) headerLines.push(`'${header.name}': ${camelName(header.name)},`);
  if (body) headerLines.push(`'Content-Type': '${body.contentType}',`);
  if (headerLines.length) options.push(`headers: {\n    ${headerLines.join('\n    ')}\n  },`);
  if (body) options.push(json ? `body: JSON.stringify(${indent(json, 2)}),` : 'body: payload,');
  const call = options.length ? `fetch(\`${url}\`, {\n  ${options.join('\n  ')}\n})` : `fetch(\`${url}\`)`;
  const read = operation.response ? 'const data = await response.json();' : 'console.log(response.status);';
  return [...comments, `const response = await ${call};`, read].join('\n');
}

export function javaSample(operation) {
  const {webhook, signIn, csrf, headers, query, body, json} = shape(operation);
  const comments = [];
  if (signIn) comments.push('// client is one HttpClient with a CookieManager, already signed in.');
  if (csrf) comments.push('// csrfHeader and csrfToken are the headerName and token GET /api/csrf returned.');
  if (webhook) comments.push('// payload is the exact body you signed with the webhook secret.');
  const parts = operation.path.split(/\{(\w+)\}/);
  const pathExpression = parts.map((part, index) => (index % 2 ? part : `"${index === 0 ? BASE : ''}${part}"`)).filter((part) => part !== '""');
  query.forEach((parameter, index) => pathExpression.push(`"${index ? '&' : '?'}${parameter.name}="`, parameter.name));
  const lines = [`HttpRequest request = HttpRequest.newBuilder(URI.create(${pathExpression.join(' + ')}))`];
  if (csrf) lines.push('        .header(csrfHeader, csrfToken)');
  for (const header of headers) lines.push(`        .header("${header.name}", ${camelName(header.name)})`);
  if (body) lines.push(`        .header("Content-Type", "${body.contentType}")`);
  const publisher = body
    ? json
      ? `HttpRequest.BodyPublishers.ofString("""\n            ${indent(json, 12)}\n            """)`
      : 'HttpRequest.BodyPublishers.ofString(payload)'
    : 'HttpRequest.BodyPublishers.noBody()';
  lines.push(
    {GET: '        .GET()', DELETE: '        .DELETE()'}[operation.method] ?? `        .method("${operation.method}", ${publisher})`,
  );
  lines.push('        .build();');
  return [
    ...comments,
    lines.join('\n'),
    'HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());',
  ].join('\n');
}

export const SAMPLE_LANGUAGES = [
  {id: 'curl', label: 'cURL', language: 'bash', render: curlSample},
  {id: 'javascript', label: 'JavaScript', language: 'javascript', render: javascriptSample},
  {id: 'java', label: 'Java', language: 'java', render: javaSample},
];
