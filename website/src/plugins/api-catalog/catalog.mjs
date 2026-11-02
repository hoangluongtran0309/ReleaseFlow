// Joins the controllers (methods, paths, parameters, schemas) with docs/api.md (groups,
// roles, proofs, errors) and the guides (behaviour), and refuses to build when they
// disagree: an unlisted mapping, a listed endpoint no controller handles, two operations
// with one address, or a type the catalog cannot describe.

import {guideMention, headingAnchor, plainHeading} from './guide.mjs';

// The areas of "Error codes by area" each group of "Endpoints" can return.
export const AREAS_BY_GROUP = {
  'account-and-organization--workspace': ['Account'],
  'projects-and-sources--sources': ['Sources'],
  'changes--changes-and-review': ['Changes', 'Categories'],
  'releases-and-notes--release-notes': ['Releases', 'Audiences', 'Glossary'],
  'automation--automation': ['Automation'],
  'signed-webhooks': ['Webhooks'],
};

const LIBRARY_TYPES = {
  String: {kind: 'string'},
  CharSequence: {kind: 'string'},
  UUID: {kind: 'string', format: 'uuid'},
  Instant: {kind: 'string', format: 'date-time'},
  OffsetDateTime: {kind: 'string', format: 'date-time'},
  ZonedDateTime: {kind: 'string', format: 'date-time'},
  LocalDateTime: {kind: 'string', format: 'date-time'},
  LocalDate: {kind: 'string', format: 'date'},
  LocalTime: {kind: 'string', format: 'time'},
  Duration: {kind: 'string', format: 'duration'},
  ZoneId: {kind: 'string', format: 'time-zone'},
  Locale: {kind: 'string', format: 'locale'},
  URI: {kind: 'string', format: 'uri'},
  URL: {kind: 'string', format: 'uri'},
  int: {kind: 'integer'},
  Integer: {kind: 'integer'},
  long: {kind: 'integer'},
  Long: {kind: 'integer'},
  short: {kind: 'integer'},
  Short: {kind: 'integer'},
  double: {kind: 'number'},
  Double: {kind: 'number'},
  float: {kind: 'number'},
  Float: {kind: 'number'},
  BigDecimal: {kind: 'number'},
  boolean: {kind: 'boolean'},
  Boolean: {kind: 'boolean'},
  'byte[]': {kind: 'binary'},
  JsonNode: {kind: 'json'},
  Object: {kind: 'json'},
};

const COLLECTIONS = /^(List|Set|Collection|SortedSet|LinkedHashSet|ArrayList|Iterable)<(.+)>$/s;
const MAPS = /^(Map|SortedMap|LinkedHashMap|TreeMap)<\s*([^,]+?)\s*,(.+)>$/s;
const REQUIRED_ANNOTATIONS = new Set(['NotNull', 'NotBlank', 'NotEmpty']);

export class CatalogError extends Error {
  constructor(problems) {
    super(`api-catalog: the REST API reference does not match the code:\n${problems.map((problem) => `  - ${problem}`).join('\n')}`);
    this.problems = problems;
  }
}

/** `get-api-projects-projectid-changes`: the method, then the path's segments. */
export function operationSlug(method, path) {
  return [method, ...path.split('/').filter(Boolean).map((segment) => segment.replace(/[{}]/g, ''))]
    .join('-')
    .toLowerCase();
}

function stripAnnotations(type) {
  return type.replace(/@[\w.]+(\([^)]*\))?\s*/g, '').replace(/\s+/g, ' ').trim();
}

function constraintsOf(annotations) {
  const constraints = [];
  for (const {name, pairs} of annotations) {
    const value = (key) => pairs[key]?.replace(/^"|"$/g, '');
    if (name === 'Size') {
      if (pairs.min) constraints.push(`min ${value('min')}`);
      if (pairs.max) constraints.push(`max ${value('max')}`);
    } else if (name === 'Min' || name === 'Max') {
      constraints.push(`${name.toLowerCase()} ${value('value')}`);
    } else if (name === 'Pattern') {
      constraints.push(`pattern ${value('regexp')}`);
    } else if (name === 'Email') {
      constraints.push('email');
    }
  }
  return constraints;
}

/** Describes a Java type as JSON: a library type, a collection, or a record, class or enum. */
export function schemaFor(typeText, from, index, stack = []) {
  const type = stripAnnotations(typeText);
  // A library type may be written with its package: java.util.UUID.
  const library = LIBRARY_TYPES[type] ?? LIBRARY_TYPES[/^[a-z]\w*(\.[a-z]\w*)*\.([A-Z]\w*)$/.exec(type)?.[2]];
  if (library) {
    return {...library};
  }
  const optional = /^Optional<(.+)>$/s.exec(type);
  if (optional) {
    return {...schemaFor(optional[1], from, index, stack), nullable: true};
  }
  const collection = COLLECTIONS.exec(type);
  if (collection) {
    return {kind: 'array', items: schemaFor(collection[2], from, index, stack)};
  }
  const map = MAPS.exec(type);
  if (map) {
    return {kind: 'map', keys: schemaFor(map[2], from, index, stack), values: schemaFor(map[3], from, index, stack)};
  }
  const name = type.replace(/<.*>$/s, '');
  const declaration = index.find(name, from);
  if (!declaration) {
    throw new Error(`cannot resolve the type ${type} (in ${from.relative})`);
  }
  if (declaration.kind === 'enum') {
    return {kind: 'enum', name, values: index.enumValues(declaration)};
  }
  if (stack.includes(name)) {
    return {kind: 'object', name, recursive: true, fields: []};
  }
  return {
    kind: 'object',
    name,
    fields: index.properties(declaration).map((property) => ({
      name: property.name,
      required: property.annotations.some((annotation) => REQUIRED_ANNOTATIONS.has(annotation.name)),
      constraints: constraintsOf(property.annotations),
      schema: schemaFor(property.type, declaration.file, index, [...stack, name]),
    })),
  };
}

/** A placeholder value for a schema, plain enough that nobody mistakes it for real data. */
export function exampleFor(schema, depth = 0) {
  switch (schema.kind) {
    case 'string':
      return schema.format ? `<${schema.format}>` : '<string>';
    case 'integer':
    case 'number':
      return 0;
    case 'boolean':
      return false;
    case 'enum':
      return schema.values[0];
    case 'array':
      return depth > 3 ? [] : [exampleFor(schema.items, depth + 1)];
    case 'map':
      return depth > 3 ? {} : {[schema.keys.kind === 'enum' ? schema.keys.values[0] : '<key>']: exampleFor(schema.values, depth + 1)};
    case 'object':
      if (schema.recursive || depth > 3) {
        return {};
      }
      return Object.fromEntries(schema.fields.map((field) => [field.name, exampleFor(field.schema, depth + 1)]));
    default:
      return null;
  }
}

// Names the rules below cannot derive, keyed by handler. A key no handler matches fails the
// build, so a renamed handler cannot leave a stale name behind.
export const NAME_OVERRIDES = {
  'RegistrationApiController#register': 'Register an Organization',
  'OrganizationMemberApiController#invite': 'Invite a member',
  'SessionApiController#current': 'Get the session',
  'CsrfApiController#csrf': 'Get a CSRF token',
  'StatusController#status': 'Get the status',
  'CategoryApiController#decide': 'Decide a category suggestion',
  'ReleaseApiController#decide': 'Decide a change',
  'AutomationWebhookController#status': 'Get a run’s status',
};

const ACRONYMS = {ai: 'AI', ui: 'UI', csrf: 'CSRF', github: 'GitHub', gitlab: 'GitLab', linear: 'Linear'};

function isPlural(word) {
  return word.endsWith('s') && !word.endsWith('ss') && !word.endsWith('us');
}

function singular(word) {
  if (!isPlural(word)) return word;
  if (word.endsWith('ies')) return `${word.slice(0, -3)}y`;
  return word.slice(0, -1);
}

function words(identifier) {
  return identifier
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/-/g, ' ')
    .toLowerCase()
    .split(' ')
    .filter(Boolean);
}

/**
 * A readable name from the handler's own name and its path: `list` on /changes is "List
 * changes", `get` on /releases/{releaseId} is "Get release", `approve` on
 * /releases/{releaseId}/approve is "Approve release", and `classifyWithAi` stays itself.
 */
export function operationName(handler, overrides = NAME_OVERRIDES) {
  const override = overrides[`${handler.controller}#${handler.javaMethod}`];
  const segments = handler.path.split('/').filter(Boolean);
  const statics = segments.filter((segment) => !segment.startsWith('{') && !['api', 'webhooks', 'public'].includes(segment));
  const last = statics[statics.length - 1] ?? '';
  const previous = statics[statics.length - 2] ?? '';
  const endsInParameter = segments[segments.length - 1]?.startsWith('{');
  const resource = words(last);
  const name = words(handler.javaMethod);
  const [verb] = name;
  let result = name;
  if (override) {
    return override;
  }
  if (name.length === 1) {
    const noun = (list) => list.join(' ');
    if (handler.path.startsWith('/webhooks/')) {
      result = [verb, statics[0], 'webhook'];
    } else if (verb === resource.join('')) {
      result = handler.method === 'GET' ? [isPlural(verb) ? 'list' : 'get', ...resource] : [verb, singular(words(previous).join(' '))];
    } else if (resource.length === 1 && resource[0] === verb) {
      result = [verb, singular(noun(words(previous)))];
    } else if (handler.method === 'GET' && !endsInParameter && !['get', 'view'].includes(verb)) {
      result = [isPlural(resource[resource.length - 1]) ? 'list' : 'get', ...resource];
    } else {
      const one = endsInParameter || ['create', 'get', 'update', 'delete', 'archive', 'edit', 'discard'].includes(verb);
      result = [verb, ...(one ? [...resource.slice(0, -1), singular(resource[resource.length - 1] ?? '')] : resource)];
    }
  }
  const text = result
    .filter(Boolean)
    .map((word) => ACRONYMS[word] ?? word)
    .join(' ');
  return text[0].toUpperCase() + text.slice(1);
}

function errorsFor(endpoint, handler, api, localApi) {
  const localNote = (code) =>
    localApi.commonErrors.flatMap((row) => row.codes).find((candidate) => candidate.code === code)?.note;
  const webhook = endpoint.group === 'signed-webhooks';
  const write = endpoint.method !== 'GET';
  const hasBody = handler.parameters.some((parameter) => parameter.in === 'body');
  const applies = {
    validation_failed: !webhook,
    malformed_request: !webhook && hasBody,
    authentication_required: !webhook && endpoint.role !== 'public',
    access_denied: !webhook && (endpoint.role === 'admin' || write),
    not_found: true,
  };
  const common = api.commonErrors.flatMap(({status, codes}) =>
    codes.filter(({code}) => applies[code] ?? true).map(({code, note}) => ({status, code, note: localNote(code) ?? note, scope: 'any'})),
  );
  const areas = AREAS_BY_GROUP[endpoint.group] ?? [];
  const area = api.areaErrors
    .filter((row) => areas.includes(row.area))
    .flatMap((row) =>
      row.see
        ? [{status: row.statuses.join(', '), code: null, see: row.see, scope: 'area', area: row.area}]
        : row.codes.map((code) => ({status: row.statuses[0], code, scope: 'area', area: row.area})),
    );
  return [...common, ...area];
}

function guideLink(guides, preferred, method, path) {
  const order = [preferred, ...Object.keys(guides).filter((doc) => doc !== preferred)].filter((doc) => guides[doc]);
  for (const doc of order) {
    const mention = guideMention(guides[doc], method, path);
    if (mention) {
      return {
        doc: doc.replace(/\.md$/, ''),
        anchor: mention.heading ? headingAnchor(mention.heading) : null,
        heading: mention.heading ? plainHeading(mention.heading) : null,
        text: mention.text,
      };
    }
  }
  return null;
}

/** Where to read more when no guide names the endpoint: its row's link, else its group's guide. */
function fallbackGuide(endpoint, group) {
  const doc = endpoint.guide?.doc ?? group?.guide;
  return doc ? {doc: doc.replace(/\.md$/, ''), anchor: endpoint.guide?.anchor ?? null, heading: null, text: null} : null;
}

/**
 * The catalog: every handler, described from the code, placed by docs/api.md, and explained
 * by its guide. `api` is the canonical (English) docs/api.md, `localApi` the one in the
 * reader's language, and `guides` the guides in that language, by file name.
 */
export function buildCatalog({handlers, index, api, localApi, guides, repository, nameOverrides = NAME_OVERRIDES}) {
  const problems = [];
  const listed = new Map(api.endpoints.map((endpoint) => [`${endpoint.method} ${endpoint.path}`, endpoint]));
  const seen = new Set();
  const slugs = new Map();
  const operations = [];

  for (const area of api.areaErrors) {
    if (!Object.values(AREAS_BY_GROUP).some((areas) => areas.includes(area.area))) {
      problems.push(`the area "${area.area}" in docs/api.md belongs to no group of the catalog (AREAS_BY_GROUP)`);
    }
  }

  for (const handler of handlers) {
    const key = `${handler.method} ${handler.path}`;
    const where = `${handler.controller}#${handler.javaMethod} (${handler.file}:${handler.line})`;
    const endpoint = listed.get(key);
    if (seen.has(key)) {
      problems.push(`\`${key}\` is handled twice, the second time by ${where}`);
      continue;
    }
    seen.add(key);
    if (!endpoint) {
      problems.push(`\`${key}\` (${where}) is not listed in docs/api.md; add it to the Endpoints tables`);
      continue;
    }
    if (!AREAS_BY_GROUP[endpoint.group]) {
      problems.push(`the group "${endpoint.group}" of docs/api.md has no areas in the catalog (AREAS_BY_GROUP)`);
    }
    const slug = operationSlug(handler.method, handler.path);
    if (slugs.has(slug)) {
      problems.push(`\`${key}\` and \`${slugs.get(slug)}\` share the address /docs/api/${slug}`);
      continue;
    }
    slugs.set(slug, key);

    const {file} = handler.context;
    let parameters;
    let body = null;
    let response = null;
    try {
      parameters = handler.parameters
        .filter((parameter) => parameter.in !== 'body')
        .map(({type, ...parameter}) => ({...parameter, schema: schemaFor(type, file, index)}));
      const bodyParameter = handler.parameters.find((parameter) => parameter.in === 'body');
      if (bodyParameter) {
        const schema = schemaFor(bodyParameter.type, file, index);
        body = {required: bodyParameter.required, contentType: handler.consumes ?? 'application/json', schema, example: exampleFor(schema)};
      }
      if (handler.returnType) {
        const schema = schemaFor(handler.returnType, file, index);
        response = {schema, example: exampleFor(schema)};
      }
    } catch (error) {
      problems.push(`${where}: ${error.message}`);
      continue;
    }

    const group = api.groups.find((candidate) => candidate.id === endpoint.group);
    operations.push({
      id: slug,
      method: handler.method,
      path: handler.path,
      name: operationName(handler, nameOverrides),
      group: endpoint.group,
      role: endpoint.role,
      proof: endpoint.proof,
      source: `${repository}/blob/develop/${handler.file}#L${handler.line}`,
      javaMethod: `${handler.controller}#${handler.javaMethod}`,
      parameters,
      body,
      response,
      statuses: handler.statuses.filter((status) => status < 300),
      errors: errorsFor(endpoint, handler, api, localApi),
      guide: guideLink(guides, endpoint.guide?.doc ?? group?.guide, handler.method, handler.path) ?? fallbackGuide(endpoint, group),
    });
  }

  const handlerKeys = new Set(handlers.map((handler) => `${handler.controller}#${handler.javaMethod}`));
  for (const key of Object.keys(nameOverrides)) {
    if (!handlerKeys.has(key)) {
      problems.push(`NAME_OVERRIDES names ${key}, which is not a handler`);
    }
  }

  for (const key of listed.keys()) {
    if (!seen.has(key)) {
      problems.push(`\`${key}\` is listed in docs/api.md, but no controller handles it`);
    }
  }
  if (problems.length) {
    throw new CatalogError(problems);
  }

  const groupOrder = api.groups.map((group) => group.id);
  operations.sort(
    (a, b) =>
      groupOrder.indexOf(a.group) - groupOrder.indexOf(b.group) ||
      api.endpoints.findIndex((endpoint) => endpoint.method === a.method && endpoint.path === a.path) -
        api.endpoints.findIndex((endpoint) => endpoint.method === b.method && endpoint.path === b.path),
  );
  const groups = api.groups
    .filter((group) => operations.some((operation) => operation.group === group.id))
    .map((group) => ({
      id: group.id,
      title: localApi.groups.find((candidate) => candidate.id === group.id)?.title ?? group.title,
      guide: group.guide?.replace(/\.md$/, '') ?? null,
    }));
  return {groups, operations};
}
