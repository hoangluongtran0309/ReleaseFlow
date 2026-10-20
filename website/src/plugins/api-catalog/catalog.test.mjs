// npm --prefix website test

import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {afterEach, test} from 'node:test';
import {readControllers, TypeIndex} from './java.mjs';
import {endpointsOfCell, guideMention, headingAnchor, parseApiGuide} from './guide.mjs';
import {buildCatalog, CatalogError, operationName, operationSlug} from './catalog.mjs';

const API = `# REST API

## Errors

| Status | Codes that can come from any endpoint |
| --- | --- |
| \`400\` | \`validation_failed\` (a field is missing), \`malformed_request\` (the body cannot be read) |
| \`401\` | \`authentication_required\` (no session) |

## Endpoints

### Changes — [Changes and review](changes.md)

| Method and path | Role |
| --- | --- |
| \`GET /api/things\`, \`POST /api/things\` | member |
| \`GET\`, \`DELETE /api/things/{thingId}\` | admin |

## Error codes by area

| Area | Status | Codes |
| --- | --- | --- |
| Changes | \`404\` | \`thing_not_found\` |
| Categories | \`409\` | \`thing_taken\` |
`;

const CONTROLLER = `package demo;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/things")
class ThingApiController {

    @GetMapping
    List<ThingView> list(@RequestParam(required = false) Kind kind) {
        return List.of();
    }

    @PostMapping
    ResponseEntity<ThingView> create(@Valid @RequestBody ThingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @GetMapping("/{thingId}")
    ThingView get(@PathVariable UUID thingId) {
        return null;
    }

    @DeleteMapping("/{thingId}")
    ResponseEntity<Void> delete(@PathVariable UUID thingId) {
        return ResponseEntity.noContent().build();
    }

    record ThingRequest(@NotBlank @Size(max = 80) String name, Kind kind) {
    }
}
`;

const TYPES = {
  'demo/ThingView.java': 'package demo;\n\nimport java.util.List;\n\npublic record ThingView(java.util.UUID id, String name, Kind kind, List<Tag> tags) {\n}\n',
  'demo/Kind.java': 'package demo;\n\npublic enum Kind {\n    SMALL,\n    LARGE\n}\n',
  'demo/Tag.java': 'package demo;\n\npublic record Tag(String label) {\n}\n',
};

let workspace;

function project(files) {
  workspace = fs.mkdtempSync(path.join(os.tmpdir(), 'api-catalog-'));
  for (const [name, content] of Object.entries(files)) {
    const file = path.join(workspace, 'src/main/java', name);
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, content);
  }
  const javaRoot = path.join(workspace, 'src/main/java');
  return {handlers: readControllers(javaRoot, workspace), index: new TypeIndex(javaRoot, workspace)};
}

function build(files, api = API, nameOverrides = {}) {
  const {handlers, index} = project(files);
  const parsed = parseApiGuide(api);
  return buildCatalog({handlers, index, api: parsed, localApi: parsed, guides: {'changes.md': '# Changes\n'}, repository: 'https://example.invalid/repo', nameOverrides});
}

afterEach(() => {
  if (workspace) {
    fs.rmSync(workspace, {recursive: true, force: true});
    workspace = null;
  }
});

test('an operation is addressed by its method and path segments', () => {
  assert.equal(operationSlug('GET', '/api/projects'), 'get-api-projects');
  assert.equal(operationSlug('POST', '/api/projects/{projectId}/changes/{changeId}/review'), 'post-api-projects-projectid-changes-changeid-review');
});

test('a table cell may share one path between several methods', () => {
  assert.deepEqual(endpointsOfCell('`GET`, `PUT`, `DELETE /api/a/{id}`'), [
    {method: 'GET', path: '/api/a/{id}'},
    {method: 'PUT', path: '/api/a/{id}'},
    {method: 'DELETE', path: '/api/a/{id}'},
  ]);
  assert.deepEqual(endpointsOfCell('`GET /api/a`, `POST /api/a`'), [
    {method: 'GET', path: '/api/a'},
    {method: 'POST', path: '/api/a'},
  ]);
});

test('docs/api.md gives each endpoint its group, role and errors', () => {
  const api = parseApiGuide(API);
  assert.deepEqual(api.groups.map((group) => [group.id, group.guide]), [['changes--changes-and-review', 'changes.md']]);
  assert.deepEqual(
    api.endpoints.map((endpoint) => `${endpoint.method} ${endpoint.path} ${endpoint.role}`),
    ['GET /api/things member', 'POST /api/things member', 'GET /api/things/{thingId} admin', 'DELETE /api/things/{thingId} admin'],
  );
  assert.deepEqual(api.commonErrors[0].codes[0], {code: 'validation_failed', note: 'a field is missing'});
  assert.equal(headingAnchor('Changes — [Changes and review](changes.md)'), 'changes--changes-and-review');
  assert.equal(headingAnchor('Hộp thay đổi {#change-inbox}'), 'change-inbox');
});

test('a guide paragraph names an endpoint alone or with a shared path', () => {
  const guide = '## Slug\n\nCall `GET`/`PUT /api/organization/slug` to read or change it.\n\n## Other\n\n```text\nGET /api/other\n```\n';
  assert.equal(guideMention(guide, 'PUT', '/api/organization/slug').heading, 'Slug');
  assert.deepEqual(guideMention(guide, 'GET', '/api/other'), {text: null, heading: 'Other'});
  assert.equal(guideMention(guide, 'DELETE', '/api/missing'), null);
});

test('the catalog describes every handler from its code', () => {
  const catalog = build({'demo/ThingApiController.java': CONTROLLER, ...TYPES});
  assert.deepEqual(
    catalog.operations.map((operation) => [operation.id, operation.name, operation.statuses]),
    [
      ['get-api-things', 'List things', [200]],
      ['post-api-things', 'Create thing', [201]],
      ['get-api-things-thingid', 'Get thing', [200]],
      ['delete-api-things-thingid', 'Delete thing', [204]],
    ],
  );
  const create = catalog.operations[1];
  assert.deepEqual(create.body.schema.fields[0], {name: 'name', required: true, constraints: ['max 80'], schema: {kind: 'string'}});
  assert.deepEqual(create.body.schema.fields[1].schema, {kind: 'enum', name: 'Kind', values: ['SMALL', 'LARGE']});
  assert.deepEqual(create.body.example, {name: '<string>', kind: 'SMALL'});
  assert.equal(catalog.operations[0].response.schema.items.fields[3].schema.items.name, 'Tag');
  assert.deepEqual(catalog.operations[0].parameters[0], {in: 'query', name: 'kind', required: false, defaultValue: undefined, schema: {kind: 'enum', name: 'Kind', values: ['SMALL', 'LARGE']}});
  assert.ok(catalog.operations[2].errors.some((error) => error.code === 'thing_taken' && error.area === 'Categories'));
  assert.ok(!catalog.operations[0].errors.some((error) => error.code === 'malformed_request'), 'no body, so no malformed body');
});

test('a request class carries the properties of the class it extends', () => {
  const controller = CONTROLLER.replace('ResponseEntity<ThingView> create(@Valid @RequestBody ThingRequest request)', 'ResponseEntity<ThingView> create(@Valid @RequestBody NewThing request)');
  const files = {
    'demo/ThingApiController.java': controller,
    ...TYPES,
    'demo/BaseThing.java': 'package demo;\n\npublic class BaseThing {\n\n    @NotBlank\n    private String name;\n\n    public String getName() {\n        return name;\n    }\n}\n',
    'demo/NewThing.java': 'package demo;\n\npublic class NewThing extends BaseThing {\n\n    private String note;\n\n    public void setNote(String note) {\n        this.note = note;\n    }\n}\n',
  };
  const create = build(files).operations.find((operation) => operation.id === 'post-api-things');
  assert.deepEqual(create.body.schema.fields.map((field) => [field.name, field.required]), [['name', true], ['note', false]]);
});

test('a mapping docs/api.md does not list stops the build', () => {
  const extra = CONTROLLER.replace('    record ThingRequest', '    @PutMapping("/{thingId}")\n    ThingView replace(@PathVariable UUID thingId) {\n        return null;\n    }\n\n    record ThingRequest');
  assert.throws(() => build({'demo/ThingApiController.java': extra, ...TYPES}), (error) => error instanceof CatalogError && /`PUT \/api\/things\/\{thingId\}` .* is not listed in docs\/api\.md/.test(error.message));
});

test('an endpoint docs/api.md lists that no controller handles stops the build', () => {
  const api = API.replace('| `GET`, `DELETE /api/things/{thingId}` | admin |', '| `GET`, `DELETE /api/things/{thingId}` | admin |\n| `POST /api/things/{thingId}/archive` | admin |');
  assert.throws(() => build({'demo/ThingApiController.java': CONTROLLER, ...TYPES}, api), /`POST \/api\/things\/\{thingId\}\/archive` is listed in docs\/api\.md, but no controller handles it/);
});

test('a type the catalog cannot describe stops the build', () => {
  const {['demo/Tag.java']: _tag, ...withoutTag} = TYPES;
  assert.throws(() => build({'demo/ThingApiController.java': CONTROLLER, ...withoutTag}), /cannot resolve the type Tag/);
});

test('two operations with one address stop the build', () => {
  const clash = CONTROLLER.replace('@GetMapping("/{thingId}")', '@GetMapping("/thingId")');
  const api = API.replace('`GET`, `DELETE /api/things/{thingId}`', '`GET /api/things/thingId`, `DELETE /api/things/{thingId}`').replace(
    '| `GET /api/things`, `POST /api/things` | member |',
    '| `GET /api/things`, `POST /api/things`, `GET /api/things/{thingId}` | member |',
  );
  const both = clash.replace('    @DeleteMapping', '    @GetMapping("/{thingId}")\n    ThingView read(@PathVariable UUID thingId) {\n        return null;\n    }\n\n    @DeleteMapping');
  assert.throws(() => build({'demo/ThingApiController.java': both, ...TYPES}, api), /share the address \/docs\/api\/get-api-things-thingid/);
});

test('a name override for a handler that no longer exists stops the build', () => {
  assert.throws(
    () => build({'demo/ThingApiController.java': CONTROLLER, ...TYPES}, API, {'ThingApiController#remove': 'Remove a thing'}),
    /NAME_OVERRIDES names ThingApiController#remove, which is not a handler/,
  );
});

test('names read as actions on the resource', () => {
  const name = (method, path, javaMethod) => operationName({method, path, javaMethod, controller: 'X'});
  assert.equal(name('POST', '/api/projects/{projectId}/releases/{releaseId}/approve', 'approve'), 'Approve release');
  assert.equal(name('GET', '/api/members', 'members'), 'List members');
  assert.equal(name('POST', '/api/projects/{projectId}/changes/{changeId}/ai-classification', 'classifyWithAi'), 'Classify with AI');
  assert.equal(name('POST', '/webhooks/github/{webhookId}', 'receive'), 'Receive GitHub webhook');
});
