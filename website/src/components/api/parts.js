// Building blocks of the REST API reference: the operations sidebar, method badges, the
// field and error tables, inline Markdown from the guides, and the samples panel.

import {useEffect, useId, useRef, useState} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import {useLocation} from '@docusaurus/router';
import useBaseUrl from '@docusaurus/useBaseUrl';
import {usePluginData} from '@docusaurus/useGlobalData';
import Translate, {translate} from '@docusaurus/Translate';
import {useLocationChange} from '@docusaurus/theme-common/internal';
import CodeBlock from '@theme/CodeBlock';
import {Icon, InlineMarkdown} from '@site/src/components/ui';
import {SAMPLE_LANGUAGES} from './samples';
import styles from './api.module.css';

const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';
const SAMPLE_KEY = 'releaseflow:api-sample-language';

export function useApiCatalog() {
  return usePluginData('api-catalog');
}

export function MethodBadge({method, size}) {
  return <span className={clsx(styles.method, styles[`method-${method.toLowerCase()}`], size === 'small' && styles.methodSmall)}>{method}</span>;
}

/** A path with its {parameters} picked out. */
export function PathText({path}) {
  return path.split(/(\{\w+\})/).map((part, index) =>
    index % 2 ? (
      <span key={index} className={styles.pathParameter}>
        {part}
      </span>
    ) : (
      part
    ),
  );
}

export function ApiSidebar({current}) {
  const {groups, operations} = useApiCatalog();
  const panelId = useId();
  const [open, setOpen] = useState(false);
  useLocationChange(() => setOpen(false));
  return (
    <nav className={styles.sidebar} aria-label={translate({id: 'api.sidebar.label', message: 'REST API operations'})}>
      <button type="button" className={styles.browse} aria-expanded={open} aria-controls={panelId} onClick={() => setOpen(!open)}>
        <Icon name={open ? 'x' : 'menu'} size={18} />
        <Translate id="api.sidebar.browse">Browse endpoints</Translate>
      </button>
      <div id={panelId} className={clsx(styles.sidePanel, open && styles.sidePanelOpen)}>
        <Link to="/docs" className={styles.back}>
          ← <Translate id="api.sidebar.docs">Docs</Translate>
        </Link>
        <Link to="/docs/api" className={styles.sideTitle} aria-current={current === 'overview' ? 'page' : undefined}>
          REST API
        </Link>
        {groups.map((group) => (
          <div key={group.id} className={styles.sideGroup}>
            <p className={styles.sideGroupLabel}>{group.title}</p>
            <ul>
              {operations
                .filter((operation) => operation.group === group.id)
                .map((operation) => (
                  <li key={operation.id}>
                    <Link
                      to={`/docs/api/${operation.id}`}
                      className={styles.sideLink}
                      aria-current={current === operation.id ? 'page' : undefined}>
                      <span>{operation.name}</span>
                      <MethodBadge method={operation.method} size="small" />
                    </Link>
                  </li>
                ))}
            </ul>
          </div>
        ))}
      </div>
    </nav>
  );
}

/** The reference's frame: the operations sidebar beside the page. */
export function ApiShell({current, children}) {
  return (
    <div className={styles.shell}>
      <ApiSidebar current={current} />
      {children}
    </div>
  );
}

function hrefOf(target, guideDoc) {
  if (/^https?:\/\//.test(target)) return target;
  if (target.startsWith('#')) return `/docs/${guideDoc}${target}`;
  if (target.startsWith('../')) return `${repository}/blob/develop/${target.slice(3)}`;
  const doc = /^([\w./-]+?)\.md(#[\w-]+)?$/.exec(target);
  return doc ? `/docs/${doc[1]}${doc[2] ?? ''}` : target;
}

/** A guide paragraph's Markdown, its links resolved against the guide it came from. */
export function GuideMarkdown({text, guideDoc}) {
  return <InlineMarkdown text={text} resolveHref={(target) => hrefOf(target, guideDoc)} />;
}

export function typeLabel(schema) {
  switch (schema.kind) {
    case 'string':
      return schema.format ? `string (${schema.format})` : 'string';
    case 'array':
      return `array of ${typeLabel(schema.items)}`;
    case 'map':
      return `map of ${typeLabel(schema.values)}`;
    case 'object':
      return schema.name;
    case 'enum':
      return `enum ${schema.name}`;
    case 'binary':
      return 'bytes';
    case 'json':
      return 'JSON';
    default:
      return schema.kind;
  }
}

/** The fields of an object, one level of nesting deep, with dotted names. */
function flatten(schema, prefix = '', depth = 0) {
  const object = schema.kind === 'array' ? schema.items : schema;
  if (object.kind !== 'object' || object.recursive) {
    return [];
  }
  return object.fields.flatMap((field) => {
    const name = `${prefix}${field.name}`;
    const row = {name, schema: field.schema, required: field.required, constraints: field.constraints};
    const inner = field.schema.kind === 'array' ? field.schema.items : field.schema;
    const nested = inner.kind === 'object' && depth < 1 ? flatten(field.schema, `${name}${field.schema.kind === 'array' ? '[]' : ''}.`, depth + 1) : [];
    return [row, ...nested];
  });
}

function Notes({schema, constraints = [], defaultValue}) {
  const notes = [...constraints];
  if (schema.nullable) notes.push('nullable');
  return (
    <>
      {schema.kind === 'enum' && (
        <span className={styles.values}>
          {schema.values.map((value) => (
            <code key={value}>{value}</code>
          ))}
        </span>
      )}
      {notes.length > 0 && <span>{notes.join(' · ')}</span>}
      {defaultValue !== undefined && (
        <span>
          <Translate id="api.table.default" values={{value: <code>{defaultValue}</code>}}>
            {'Default {value}'}
          </Translate>
        </span>
      )}
    </>
  );
}

function RequiredCell({required}) {
  return required ? (
    <span className={styles.required}>
      <Translate id="api.required">Required</Translate>
    </span>
  ) : (
    <span className={styles.optional}>
      <Translate id="api.optional">Optional</Translate>
    </span>
  );
}

function Table({caption, children, head}) {
  return (
    <div className={styles.tableWrap} tabIndex={0} role="group" aria-label={caption}>
      <table className={styles.table}>
        <thead>
          <tr>{head}</tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

const headings = {
  name: () => <Translate id="api.table.name">Name</Translate>,
  field: () => <Translate id="api.table.field">Field</Translate>,
  type: () => <Translate id="api.table.type">Type</Translate>,
  required: () => <Translate id="api.table.required">Required</Translate>,
  notes: () => <Translate id="api.table.notes">Notes</Translate>,
};

export function ParameterTable({caption, parameters}) {
  return (
    <Table
      caption={caption}
      head={['name', 'type', 'required', 'notes'].map((key) => (
        <th key={key} scope="col">
          {headings[key]()}
        </th>
      ))}>
      {parameters.map((parameter) => (
        <tr key={parameter.name}>
          <td>
            <code>{parameter.name}</code>
          </td>
          <td>{typeLabel(parameter.schema)}</td>
          <td>
            <RequiredCell required={parameter.required} />
          </td>
          <td>
            <Notes schema={parameter.schema} defaultValue={parameter.defaultValue} />
          </td>
        </tr>
      ))}
    </Table>
  );
}

export function SchemaTable({caption, schema}) {
  const rows = flatten(schema);
  return (
    <>
      {schema.kind === 'array' && (
        <p className={styles.note}>
          <Translate id="api.schema.arrayOf" values={{name: <code>{typeLabel(schema.items)}</code>}}>
            {'An array of {name}, each with:'}
          </Translate>
        </p>
      )}
      {rows.length > 0 ? (
        <Table
          caption={caption}
          head={['field', 'type', 'required', 'notes'].map((key) => (
            <th key={key} scope="col">
              {headings[key]()}
            </th>
          ))}>
          {rows.map((row) => (
            <tr key={row.name}>
              <td>
                <code>{row.name}</code>
              </td>
              <td>{typeLabel(row.schema)}</td>
              <td>
                <RequiredCell required={row.required} />
              </td>
              <td>
                <Notes schema={row.schema} constraints={row.constraints} />
              </td>
            </tr>
          ))}
        </Table>
      ) : (
        schema.kind !== 'array' && (
          <p className={styles.note}>
            <code>{typeLabel(schema)}</code>
          </p>
        )
      )}
    </>
  );
}

export function ErrorTable({errors, guideDoc}) {
  return (
    <Table
      caption={translate({id: 'api.section.errors', message: 'Errors'})}
      head={
        <>
          <th scope="col">
            <Translate id="api.errors.status">Status</Translate>
          </th>
          <th scope="col">
            <Translate id="api.errors.code">Code</Translate>
          </th>
          <th scope="col">
            <Translate id="api.errors.scope">Applies to</Translate>
          </th>
        </>
      }>
      {errors.map((error, index) => (
        <tr key={`${error.code ?? error.area}-${index}`}>
          <td>
            <code>{error.status}</code>
          </td>
          <td>
            {error.code ? (
              <>
                <code>{error.code}</code>
                {error.note && <span className={styles.errorNote}>{error.note}</span>}
              </>
            ) : (
              <Link to={hrefOf(`${error.see.doc}${error.see.anchor ? `#${error.see.anchor}` : ''}`, guideDoc)}>
                <Translate id="api.errors.see">See the guide</Translate>
              </Link>
            )}
          </td>
          <td>
            {error.scope === 'any' ? (
              <Translate id="api.errors.any">Any endpoint</Translate>
            ) : (
              <Translate id="api.errors.area" values={{area: error.area}}>
                {'{area} area'}
              </Translate>
            )}
          </td>
        </tr>
      ))}
    </Table>
  );
}

/** JSON with its keys and strings coloured. */
export function JsonView({value, label}) {
  const json = JSON.stringify(value, null, 2);
  const parts = [];
  let last = 0;
  for (const match of json.matchAll(/("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d+)?/g)) {
    parts.push(json.slice(last, match.index));
    if (match[1]) {
      parts.push(
        <span key={match.index} className={match[2] ? styles.jsonKey : styles.jsonString}>
          {match[1]}
        </span>,
      );
      if (match[2]) parts.push(match[2]);
    } else {
      parts.push(
        <span key={match.index} className={styles.jsonLiteral}>
          {match[0]}
        </span>,
      );
    }
    last = match.index + match[0].length;
  }
  parts.push(json.slice(last));
  return (
    <pre className={styles.json} tabIndex={0} aria-label={label}>
      <code>{parts}</code>
    </pre>
  );
}

function readLanguage() {
  try {
    const stored = window.localStorage.getItem(SAMPLE_KEY);
    return SAMPLE_LANGUAGES.some((language) => language.id === stored) ? stored : null;
  } catch {
    return null;
  }
}

/** cURL, JavaScript or Java; the choice follows the reader from one operation to the next. */
export function SamplesPanel({operation}) {
  const [selected, setSelected] = useState(SAMPLE_LANGUAGES[0].id);
  const tabs = useRef([]);
  const baseId = useId();
  useEffect(() => {
    const stored = readLanguage();
    if (stored) {
      setSelected(stored);
    }
  }, []);

  function choose(id, focus) {
    setSelected(id);
    try {
      window.localStorage.setItem(SAMPLE_KEY, id);
    } catch {
      // Storage is unavailable; the choice lasts until the page is left.
    }
    if (focus) {
      tabs.current[SAMPLE_LANGUAGES.findIndex((language) => language.id === id)]?.focus();
    }
  }

  function onKeyDown(event) {
    const index = SAMPLE_LANGUAGES.findIndex((language) => language.id === selected);
    const move = {ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: SAMPLE_LANGUAGES.length - 1}[event.key];
    if (move !== undefined) {
      event.preventDefault();
      choose(SAMPLE_LANGUAGES[(move + SAMPLE_LANGUAGES.length) % SAMPLE_LANGUAGES.length].id, true);
    }
  }

  const language = SAMPLE_LANGUAGES.find((candidate) => candidate.id === selected);
  return (
    <section className={styles.panel} aria-labelledby={`${baseId}-title`}>
      <div className={styles.panelHead}>
        <h2 id={`${baseId}-title`} className={styles.panelTitle}>
          <Translate id="api.samples.title">Request</Translate>
        </h2>
        <div className={styles.tabs} role="tablist" aria-label={translate({id: 'api.samples.languages', message: 'Sample language'})} onKeyDown={onKeyDown}>
          {SAMPLE_LANGUAGES.map((candidate, index) => (
            <button
              key={candidate.id}
              ref={(element) => {
                tabs.current[index] = element;
              }}
              type="button"
              role="tab"
              id={`${baseId}-${candidate.id}`}
              aria-selected={candidate.id === selected}
              aria-controls={`${baseId}-panel`}
              tabIndex={candidate.id === selected ? 0 : -1}
              className={styles.tab}
              onClick={() => choose(candidate.id, false)}>
              {candidate.label}
            </button>
          ))}
        </div>
      </div>
      <div id={`${baseId}-panel`} role="tabpanel" aria-labelledby={`${baseId}-${selected}`} className={styles.sample}>
        <CodeBlock language={language.language}>{language.render(operation)}</CodeBlock>
      </div>
    </section>
  );
}

export function ResponsePanel({operation}) {
  const titleId = useId();
  const status = operation.statuses[0];
  return (
    <section className={styles.panel} aria-labelledby={titleId}>
      <div className={styles.panelHead}>
        <h2 id={titleId} className={styles.panelTitle}>
          <Translate id="api.response.title">Response</Translate>
        </h2>
        <span className={styles.status}>{status}</span>
      </div>
      {operation.response ? (
        <>
          <p className={styles.exampleLabel}>
            <Translate id="api.example">Example</Translate>
          </p>
          <JsonView value={operation.response.example} label={translate({id: 'api.response.example', message: 'Example response body'})} />
        </>
      ) : (
        <p className={styles.panelNote}>
          <Translate id="api.response.none">No response body.</Translate>
        </p>
      )}
    </section>
  );
}

export function useOperationHref() {
  const docsApi = useBaseUrl('/docs/api');
  const {pathname} = useLocation();
  return {docsApi, current: pathname.replace(/\/$/, '').slice(docsApi.length + 1) || 'overview'};
}
