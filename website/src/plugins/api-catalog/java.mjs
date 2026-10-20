// Reads the REST controllers and the types they use with java-parser, so the API
// reference is generated from the code rather than written beside it. Only structure comes
// from the syntax tree (classes, methods, parameters, annotations, record components); the
// small pieces of source an annotation or a constant holds are read back as text.

import fs from 'node:fs';
import path from 'node:path';
import {parse} from 'java-parser';

const MAPPINGS = {GetMapping: 'GET', PostMapping: 'POST', PutMapping: 'PUT', DeleteMapping: 'DELETE', PatchMapping: 'PATCH'};

/** Text matched literally inside a regular expression. */
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// Constants an annotation may name that live in a library rather than in this code base.
const LIBRARY_CONSTANTS = {
  'MediaType.APPLICATION_JSON_VALUE': 'application/json',
  'MediaType.TEXT_PLAIN_VALUE': 'text/plain',
};

const STATUS_CODES = {
  OK: 200,
  CREATED: 201,
  ACCEPTED: 202,
  NO_CONTENT: 204,
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  PAYLOAD_TOO_LARGE: 413,
  CONTENT_TOO_LARGE: 413,
  UNPROCESSABLE_ENTITY: 422,
  UNPROCESSABLE_CONTENT: 422,
  TOO_MANY_REQUESTS: 429,
  BAD_GATEWAY: 502,
  SERVICE_UNAVAILABLE: 503,
};

function children(node, name) {
  return node?.children?.[name] ?? [];
}

function first(node, name) {
  return children(node, name)[0];
}

/** Every descendant called `name`, without looking inside a match. */
function findAll(node, name, found = []) {
  for (const list of Object.values(node?.children ?? {})) {
    for (const child of list) {
      if (child.name === name) {
        found.push(child);
      } else if (child.children) {
        findAll(child, name, found);
      }
    }
  }
  return found;
}

function bounds(node) {
  if (node.image !== undefined) {
    return [node.startOffset, node.endOffset];
  }
  let start = Infinity;
  let end = -Infinity;
  for (const list of Object.values(node.children)) {
    for (const child of list) {
      const [s, e] = bounds(child);
      start = Math.min(start, s);
      end = Math.max(end, e);
    }
  }
  return [start, end];
}

class JavaFile {
  constructor(file, root) {
    this.file = file;
    this.relative = path.relative(root, file).split(path.sep).join('/');
    this.source = fs.readFileSync(file, 'utf8');
    try {
      this.cst = parse(this.source);
    } catch (error) {
      throw new Error(`api-catalog: cannot parse ${this.relative}: ${error.message}`);
    }
  }

  text(node) {
    const [start, end] = bounds(node);
    return this.source.slice(start, end + 1);
  }

  line(node) {
    const [start] = bounds(node);
    return this.source.slice(0, start).split('\n').length;
  }
}

function annotationsOf(file, nodes) {
  return nodes.flatMap((node) => children(node, 'annotation')).map((annotation) => {
    const name = file.text(first(annotation, 'typeName'));
    const value = first(annotation, 'elementValue');
    const pairs = {};
    for (const pair of findAll(first(annotation, 'elementValuePairList') ?? {}, 'elementValuePair')) {
      pairs[pair.children.Identifier[0].image] = file.text(first(pair, 'elementValue'));
    }
    if (value) {
      pairs.value = file.text(value);
    }
    return {name, pairs};
  });
}

/** Classes, records and enums declared in a file, nested ones included. */
function typeDeclarations(file) {
  const declarations = [];
  // A class's modifiers, annotations included, belong to the classDeclaration around it.
  const visit = (node, outer) => {
    for (const list of Object.values(node.children ?? {})) {
      for (const child of list) {
        if (child.name === 'classDeclaration') {
          const [kind, inner] = Object.entries({class: 'normalClassDeclaration', record: 'recordDeclaration', enum: 'enumDeclaration'})
            .map(([name, rule]) => [name, first(child, rule)])
            .find(([, found]) => found) ?? [];
          if (inner) {
            const declaration = {kind, name: file.text(first(inner, 'typeIdentifier')), node: inner, modifiers: children(child, 'classModifier'), file, outer};
            declarations.push(declaration);
            visit(inner, declaration);
            continue;
          }
        }
        // java-parser 3.0.1 reads a record nested in a class as a method whose result type is
        // "record"; its parameters are the record's components.
        if (child.name === 'methodDeclaration' && file.text(first(first(child, 'methodHeader'), 'result')) === 'record') {
          const name = first(first(child, 'methodHeader'), 'methodDeclarator').children.Identifier[0].image;
          declarations.push({kind: 'record', name, node: child, nestedRecord: true, modifiers: [], file, outer});
          continue;
        }
        if (child.children) {
          visit(child, outer);
        }
      }
    }
  };
  visit(file.cst, null);
  return declarations;
}

function memberDeclarations(declaration, memberName) {
  const body = first(declaration.node, 'classBody') ?? first(declaration.node, 'recordBody') ?? first(declaration.node, 'enumBody');
  const members = [];
  for (const bodyDeclaration of [...children(body, 'classBodyDeclaration'), ...children(body, 'recordBodyDeclaration')]) {
    const member = first(first(bodyDeclaration, 'classMemberDeclaration') ?? bodyDeclaration, memberName);
    if (member) {
      members.push(member);
    }
  }
  const enumDeclarations = first(body, 'enumBodyDeclarations');
  for (const bodyDeclaration of children(enumDeclarations, 'classBodyDeclaration')) {
    const member = first(first(bodyDeclaration, 'classMemberDeclaration'), memberName);
    if (member) {
      members.push(member);
    }
  }
  return members;
}

/** `static final String NAME = "value"` fields of a declaration. */
function stringConstants(declaration) {
  const constants = {};
  for (const field of memberDeclarations(declaration, 'fieldDeclaration')) {
    const modifiers = children(field, 'fieldModifier').map((modifier) => declaration.file.text(modifier));
    if (!modifiers.includes('static') || !modifiers.includes('final')) {
      continue;
    }
    for (const declarator of findAll(field, 'variableDeclarator')) {
      const initializer = first(declarator, 'variableInitializer');
      const literal = initializer && declaration.file.text(initializer);
      if (literal && /^"([^"\\]|\\.)*"$/.test(literal)) {
        constants[declaration.file.text(first(declarator, 'variableDeclaratorId'))] = JSON.parse(literal);
      }
    }
  }
  return constants;
}

/** An annotation value as a string: a literal, a constant of the class, or a library constant. */
function stringValue(expression, constants, where) {
  if (expression === undefined) {
    return undefined;
  }
  const trimmed = expression.trim();
  if (/^"([^"\\]|\\.)*"$/.test(trimmed)) {
    return JSON.parse(trimmed);
  }
  if (trimmed in constants) {
    return constants[trimmed];
  }
  if (trimmed in LIBRARY_CONSTANTS) {
    return LIBRARY_CONSTANTS[trimmed];
  }
  throw new Error(`api-catalog: cannot read the value ${trimmed} in ${where}`);
}

function joinPaths(prefix, suffix) {
  const joined = `${prefix ?? ''}/${suffix ?? ''}`.replace(/\/+/g, '/');
  return joined.length > 1 ? joined.replace(/\/$/, '') : joined;
}

function successStatuses(body, returnsBody) {
  const statuses = new Set();
  for (const match of body.matchAll(/HttpStatus\.([A-Z_]+)/g)) {
    if (STATUS_CODES[match[1]]) {
      statuses.add(STATUS_CODES[match[1]]);
    }
  }
  if (/\.noContent\(\)/.test(body)) statuses.add(204);
  if (/ResponseEntity\.accepted\(\)/.test(body)) statuses.add(202);
  if (/ResponseEntity\.created\(/.test(body)) statuses.add(201);
  if (/ResponseEntity\.ok\b/.test(body)) statuses.add(200);
  if (![...statuses].some((status) => status < 300)) {
    statuses.add(returnsBody ? 200 : 204);
  }
  return [...statuses].sort();
}

function parameterOf(file, parameter, constants, where) {
  const regular = first(parameter, 'variableParaRegularParameter');
  if (!regular) {
    return null;
  }
  const annotations = annotationsOf(file, children(regular, 'variableModifier'));
  const type = file.text(first(regular, 'unannType'));
  const identifier = file.text(first(regular, 'variableDeclaratorId'));
  const read = (annotation) => stringValue(annotation.pairs.name ?? annotation.pairs.value, constants, where) ?? identifier;
  const isTrue = (value, fallback) => (value === undefined ? fallback : value.trim() !== 'false');
  for (const annotation of annotations) {
    switch (annotation.name) {
      case 'PathVariable':
        return {in: 'path', name: read(annotation), type, required: true};
      case 'RequestParam': {
        const defaultValue = stringValue(annotation.pairs.defaultValue, constants, where);
        return {in: 'query', name: read(annotation), type, required: isTrue(annotation.pairs.required, defaultValue === undefined), defaultValue};
      }
      case 'RequestHeader':
        return {in: 'header', name: read(annotation), type, required: isTrue(annotation.pairs.required, true)};
      case 'RequestBody':
        return {in: 'body', name: identifier, type, required: isTrue(annotation.pairs.required, true)};
      case 'ModelAttribute':
      case 'CookieValue':
      case 'MatrixVariable':
        throw new Error(`api-catalog: @${annotation.name} in ${where} is not supported by the catalog`);
      default:
    }
  }
  return null;
}

/** Every handler of every @RestController under `javaRoot`. */
export function readControllers(javaRoot, repositoryRoot) {
  const files = listJavaFiles(javaRoot).filter((file) => /@RestController\b/.test(fs.readFileSync(file, 'utf8')));
  const handlers = [];
  for (const filePath of files) {
    const file = new JavaFile(filePath, repositoryRoot);
    for (const declaration of typeDeclarations(file)) {
      const annotations = annotationsOf(file, declaration.modifiers);
      if (!annotations.some((annotation) => annotation.name === 'RestController')) {
        continue;
      }
      const constants = stringConstants(declaration);
      const classMapping = annotations.find((annotation) => annotation.name === 'RequestMapping');
      const prefix = classMapping && stringValue(classMapping.pairs.path ?? classMapping.pairs.value, constants, file.relative);
      const methods = memberDeclarations(declaration, 'methodDeclaration').filter(
        (method) => file.text(first(first(method, 'methodHeader'), 'result')) !== 'record',
      );
      // A handler may build its response in a helper of the same class, called unqualified;
      // the helper's status counts too.
      const helpers = Object.fromEntries(
        methods
          .filter((method) => !annotationsOf(file, children(method, 'methodModifier')).some((annotation) => MAPPINGS[annotation.name]))
          .map((method) => [
            first(first(method, 'methodHeader'), 'methodDeclarator').children.Identifier[0].image,
            file.text(first(method, 'methodBody')),
          ]),
      );
      const withHelpers = (body) =>
        [body, ...Object.entries(helpers).filter(([name]) => new RegExp(`(^|[^.\\w])${name}\\(`).test(body)).map(([, text]) => text)].join('\n');
      for (const method of methods) {
        const methodAnnotations = annotationsOf(file, children(method, 'methodModifier'));
        const header = first(method, 'methodHeader');
        const javaMethod = first(header, 'methodDeclarator').children.Identifier[0].image;
        const where = `${file.relative} ${declaration.name}#${javaMethod}`;
        if (methodAnnotations.some((annotation) => annotation.name === 'RequestMapping')) {
          throw new Error(`api-catalog: ${where} uses a method-level @RequestMapping; name the HTTP method instead`);
        }
        const mapping = methodAnnotations.find((annotation) => MAPPINGS[annotation.name]);
        if (!mapping) {
          continue;
        }
        const returnType = file.text(first(header, 'result'));
        const unwrapped = returnType.replace(/^ResponseEntity<(.*)>$/s, '$1').trim();
        const returnsBody = !['void', 'Void', 'ResponseEntity<?>'].includes(unwrapped);
        const parameters = findAll(first(first(header, 'methodDeclarator'), 'formalParameterList') ?? {}, 'formalParameter')
          .map((parameter) => parameterOf(file, parameter, constants, where))
          .filter(Boolean);
        const responseStatus = methodAnnotations.find((annotation) => annotation.name === 'ResponseStatus');
        const body = withHelpers(file.text(first(method, 'methodBody')));
        handlers.push({
          controller: declaration.name,
          file: file.relative,
          line: file.line(method),
          javaMethod,
          method: MAPPINGS[mapping.name],
          path: joinPaths(prefix, stringValue(mapping.pairs.path ?? mapping.pairs.value, constants, where)),
          consumes: stringValue(mapping.pairs.consumes, constants, where),
          parameters,
          returnType: returnsBody ? unwrapped : null,
          statuses: responseStatus
            ? [STATUS_CODES[responseStatus.pairs.value.replace(/^HttpStatus\./, '')]]
            : successStatuses(body, returnsBody),
          context: {file, declaration},
        });
      }
    }
  }
  return handlers;
}

function listJavaFiles(directory) {
  return fs.readdirSync(directory, {withFileTypes: true}).flatMap((entry) => {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return listJavaFiles(full);
    }
    return entry.name.endsWith('.java') ? [full] : [];
  });
}

/**
 * Finds the declarations of the types the handlers use, parsing a file only when its text
 * declares a type of that name. Names are simple names; a clash is resolved by the
 * referring file's package, then by its imports.
 */
export class TypeIndex {
  constructor(javaRoot, repositoryRoot) {
    this.repositoryRoot = repositoryRoot;
    this.sources = listJavaFiles(javaRoot).map((file) => ({file, text: fs.readFileSync(file, 'utf8')}));
    this.parsed = new Map();
  }

  parsedFile(file) {
    if (!this.parsed.has(file)) {
      const javaFile = new JavaFile(file, this.repositoryRoot);
      this.parsed.set(file, {javaFile, declarations: typeDeclarations(javaFile)});
    }
    return this.parsed.get(file);
  }

  find(qualified, from) {
    // Outer.Inner names a type declared inside another.
    const parts = qualified.split('.');
    if (parts.length > 1) {
      const outer = this.find(parts.slice(0, -1).join('.'), from);
      const inner = parts[parts.length - 1];
      return outer && this.parsedFile(outer.file.file).declarations.find((declaration) => declaration.outer === outer && declaration.name === inner);
    }
    const name = qualified;
    const pattern = new RegExp(`\\b(class|record|enum|interface)\\s+${name}\\b`);
    const candidates = this.sources
      .filter((source) => pattern.test(source.text))
      .flatMap((source) => this.parsedFile(source.file).declarations.filter((declaration) => declaration.name === name));
    if (candidates.length <= 1) {
      return candidates[0];
    }
    const packageOf = (file) => /^package\s+([\w.]+);/m.exec(file.source)?.[1];
    const fromPackage = packageOf(from);
    const nested = candidates.find((candidate) => candidate.file.file === from.file);
    const samePackage = candidates.filter((candidate) => packageOf(candidate.file) === fromPackage);
    const imported = candidates.filter((candidate) =>
      new RegExp(`^import\\s+${escapeRegExp(packageOf(candidate.file) ?? '')}\\.${name};`, 'm').test(from.source),
    );
    const chosen = nested ?? (samePackage.length === 1 ? samePackage[0] : imported[0]);
    if (!chosen) {
      throw new Error(`api-catalog: the type ${name} used in ${from.relative} is declared more than once`);
    }
    return chosen;
  }

  /** The properties a JSON body of this declaration carries. */
  properties(declaration) {
    const {file} = declaration;
    if (declaration.nestedRecord) {
      const parameters = findAll(first(first(first(declaration.node, 'methodHeader'), 'methodDeclarator'), 'formalParameterList') ?? {}, 'formalParameter');
      return parameters.map((parameter) => {
        const regular = first(parameter, 'variableParaRegularParameter');
        return {
          name: file.text(first(regular, 'variableDeclaratorId')),
          type: file.text(first(regular, 'unannType')),
          annotations: annotationsOf(file, children(regular, 'variableModifier')),
        };
      });
    }
    if (declaration.kind === 'record') {
      const components = findAll(first(declaration.node, 'recordHeader') ?? {}, 'recordComponent');
      return components.map((component) => ({
        name: component.children.Identifier[0].image,
        type: file.text(first(component, 'unannType')),
        annotations: annotationsOf(file, children(component, 'recordComponentModifier')),
      }));
    }
    // A class's JSON also carries what its superclass declares, first.
    const superclass = first(first(declaration.node, 'classExtends'), 'classType');
    const inherited = superclass ? this.find(file.text(superclass).replace(/<.*>$/s, ''), file) : null;
    if (superclass && !inherited) {
      throw new Error(`cannot resolve the superclass ${file.text(superclass)} of ${declaration.name} (in ${file.relative})`);
    }
    const methods = memberDeclarations(declaration, 'methodDeclaration').map(
      (method) => first(first(method, 'methodHeader'), 'methodDeclarator').children.Identifier[0].image,
    );
    const own = memberDeclarations(declaration, 'fieldDeclaration')
      .filter((field) => !children(field, 'fieldModifier').some((modifier) => file.text(modifier) === 'static'))
      .flatMap((field) =>
        findAll(field, 'variableDeclarator').map((declarator) => ({
          name: file.text(first(declarator, 'variableDeclaratorId')),
          type: file.text(first(field, 'unannType')),
          annotations: annotationsOf(file, children(field, 'fieldModifier')),
        })),
      )
      .filter(({name}) => {
        const capitalized = name[0].toUpperCase() + name.slice(1);
        return methods.some((method) => [`get${capitalized}`, `is${capitalized}`, `set${capitalized}`].includes(method));
      });
    return [...(inherited ? this.properties(inherited) : []), ...own];
  }

  enumValues(declaration) {
    const body = first(declaration.node, 'enumBody');
    return findAll(first(body, 'enumConstantList') ?? {}, 'enumConstant').map((constant) => constant.children.Identifier[0].image);
  }
}
