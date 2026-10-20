// Reads what docs/api.md and the guides say about each endpoint: its group, role or proof,
// the error tables, and the paragraph of a guide that describes it.

const METHOD = /^(GET|POST|PUT|PATCH|DELETE)$/;
const ENDPOINT = /^(GET|POST|PUT|PATCH|DELETE) (\/\S+)$/;

/** Text matched literally inside a regular expression. */
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** The anchor Docusaurus gives a heading: its explicit {#id}, or GitHub's slug of its text. */
export function headingAnchor(heading) {
  const explicit = /\{#([^}]+)\}\s*$/.exec(heading);
  if (explicit) {
    return explicit[1];
  }
  return plainHeading(heading)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/ /g, '-');
}

/** A heading's words without its link syntax, code marks or explicit id. */
export function plainHeading(heading) {
  return heading
    .replace(/\{#[^}]+\}\s*$/, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/`/g, '')
    .trim();
}

function cells(row) {
  return row
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim());
}

function codeSpans(text) {
  return [...text.matchAll(/`([^`]+)`/g)].map((match) => match[1]);
}

function isTableRow(line) {
  return line.trim().startsWith('|') && !/^\|[\s|:-]+\|$/.test(line.trim());
}

/** The endpoints a table cell names: `GET /a`, or `GET`, `PUT`, `DELETE /a` sharing a path. */
export function endpointsOfCell(cell) {
  const endpoints = [];
  let pending = [];
  for (const span of codeSpans(cell)) {
    if (METHOD.test(span)) {
      pending.push(span);
    } else {
      const match = ENDPOINT.exec(span);
      if (match) {
        for (const method of [...pending, match[1]]) {
          endpoints.push({method, path: match[2]});
        }
      }
      pending = [];
    }
  }
  return endpoints;
}

/**
 * Groups and endpoints from the "Endpoints" section, the codes any endpoint can return from
 * "Errors", and the codes of each area from "Error codes by area".
 */
export function parseApiGuide(markdown) {
  const groups = [];
  const endpoints = [];
  const commonErrors = [];
  const areaErrors = [];
  let section = null;
  let group = null;
  let header = true;
  for (const line of markdown.split('\n')) {
    const h2 = /^## (.+)$/.exec(line);
    const h3 = /^### (.+)$/.exec(line);
    if (h2) {
      section = headingAnchor(h2[1]);
      group = null;
      header = true;
      continue;
    }
    if (h3 && section === 'endpoints') {
      const link = /\]\(([\w-]+\.md)(#[\w-]+)?\)/.exec(h3[1]);
      group = {id: headingAnchor(h3[1]), title: plainHeading(h3[1]).split(' — ')[0], guide: link?.[1] ?? null};
      groups.push(group);
      header = true;
      continue;
    }
    if (!line.trim().startsWith('|')) {
      header = true;
      continue;
    }
    if (!isTableRow(line)) {
      continue;
    }
    if (header) {
      header = false;
      continue;
    }
    const row = cells(line);
    if (section === 'endpoints' && group) {
      const link = /\]\(([\w-]+\.md)(#([\w-]+))?\)/.exec(row[2] ?? '');
      const proof = row.length > 2 ? codeSpans(row[1]) : null;
      for (const endpoint of endpointsOfCell(row[0])) {
        endpoints.push({
          ...endpoint,
          group: group.id,
          role: proof ? null : row[1].replace(/\*/g, ''),
          proof: proof ? row[1] : null,
          guide: link ? {doc: link[1], anchor: link[3] ?? null} : null,
        });
      }
    } else if (section === 'errors') {
      const status = Number(codeSpans(row[0])[0]);
      const codes = [...row[1].matchAll(/`([a-z_]+)`(?:\s*\(([^)]*)\))?/g)].map((match) => ({code: match[1], note: match[2] ?? null}));
      commonErrors.push({status, codes});
    } else if (section === 'error-codes-by-area') {
      const link = /\]\(([\w-]+\.md)(#([\w-]+))?\)/.exec(row[2]);
      areaErrors.push({
        area: row[0],
        statuses: codeSpans(row[1]).map(Number),
        codes: link ? [] : codeSpans(row[2]),
        see: link ? {doc: link[1], anchor: link[3] ?? null} : null,
      });
    }
  }
  return {groups, endpoints, commonErrors, areaErrors};
}

/**
 * Where a guide talks about an endpoint: the first prose paragraph naming it, or failing
 * that the section whose code block lists it. Returns the paragraph, if any, and the
 * heading it sits under.
 */
export function guideMention(markdown, method, path) {
  // `GET /a`, or a shared path such as `GET`/`PUT /a`.
  const names = (text) => text.includes(`\`${method} ${path}\``) || (text.includes(` ${path}\``) && text.includes(`\`${method}\``));
  const lines = markdown.split('\n');
  let heading = null;
  let fence = false;
  let paragraph = [];
  let paragraphHeading = null;
  let codeHeading = null;
  const flush = () => {
    const text = paragraph.join(' ').trim();
    paragraph = [];
    return text;
  };
  for (const line of [...lines, '']) {
    if (/^```/.test(line.trim())) {
      if (!fence) {
        const text = flush();
        if (names(text)) {
          return {text, heading: paragraphHeading};
        }
      }
      fence = !fence;
      continue;
    }
    if (fence) {
      if (!codeHeading && heading && new RegExp(`(^|\\s)${method}\\s+${escapeRegExp(path)}(\\s|$)`).test(line)) {
        codeHeading = heading;
      }
      continue;
    }
    const isHeading = /^#{2,4} /.exec(line);
    if (!line.trim() || isHeading || isTableRow(line) || line.trim().startsWith('|')) {
      const text = flush();
      if (names(text)) {
        return {text, heading: paragraphHeading};
      }
      if (isHeading) {
        heading = line.replace(/^#+ /, '');
      }
      continue;
    }
    if (!paragraph.length) {
      paragraphHeading = heading;
    }
    paragraph.push(line.trim());
  }
  return codeHeading ? {text: null, heading: codeHeading} : null;
}
