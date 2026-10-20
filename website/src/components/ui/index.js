// The building blocks the site's own pages share: bands, headings, buttons, cards, code,
// lists and disclosure. Each one styles itself from the design tokens in custom.css.

import {useEffect, useId, useRef, useState} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useBrokenLinks from '@docusaurus/useBrokenLinks';
import Translate, {translate} from '@docusaurus/Translate';
import Heading from '@theme/Heading';
import Icon from './Icon';
import styles from './ui.module.css';

export {Icon};

export function isExternal(href) {
  return /^https?:\/\//.test(href ?? '');
}

/** The external-link mark, with words for a screen reader. */
export function ExternalMark({size = 12}) {
  return (
    <>
      <Icon name="external-link" size={size} strokeWidth={2.2} />
      <span className={styles.visuallyHidden}>
        {translate({
          id: 'theme.IconExternalLink.ariaLabel',
          message: '(opens in new tab)',
          description: 'The ARIA label for the external link icon',
        })}
      </span>
    </>
  );
}

export function Container({className, size, children}) {
  return <div className={clsx(styles.container, size === 'narrow' && styles.narrow, className)}>{children}</div>;
}

/** A band of the page. `band` is deep (heroes, closing bands), default, or alt. */
export function Section({band = 'default', id, labelledBy, className, children}) {
  // Registered so a link to the section is checked by the build like a heading's anchor.
  const brokenLinks = useBrokenLinks();
  if (id) {
    brokenLinks.collectAnchor(id);
  }
  return (
    <section id={id} aria-labelledby={labelledBy} className={clsx(styles.section, styles[band], className)}>
      {children}
    </section>
  );
}

/** The first band of a page: deep ground, hero spacing, and a rule below. */
export function Hero({labelledBy, className, children}) {
  return (
    <section aria-labelledby={labelledBy} className={clsx(styles.hero, styles.deep, className)}>
      {children}
    </section>
  );
}

export function Eyebrow({children, className}) {
  return <p className={clsx(styles.eyebrow, className)}>{children}</p>;
}

export function SectionHeader({eyebrow, title, id, lead, align, className, children}) {
  return (
    <div className={clsx(styles.sectionHeader, align === 'center' && styles.center, className)}>
      {eyebrow && <Eyebrow>{eyebrow}</Eyebrow>}
      <Heading as="h2" id={id} className={styles.h2}>
        {title}
      </Heading>
      {lead && <p className={styles.body}>{lead}</p>}
      {children}
    </div>
  );
}

/** A primary or ghost button that navigates. External targets get the external mark. */
export function ButtonLink({to, variant = 'primary', icon, arrow, children, className}) {
  const external = isExternal(to);
  return (
    <Link to={to} className={clsx(styles.button, styles[variant], className)}>
      {icon && <Icon name={icon} size={16} />}
      {children}
      {arrow && !external && <Icon name="arrow-right" size={16} />}
      {external && <ExternalMark size={14} />}
    </Link>
  );
}

/** A primary or ghost button that does something on this page rather than navigate. */
export function ActionButton({onClick, variant = 'primary', icon, children, className, ...props}) {
  return (
    <button type="button" onClick={onClick} className={clsx(styles.button, styles[variant], className)} {...props}>
      {icon && <Icon name={icon} size={16} />}
      {children}
    </button>
  );
}

export function TextLink({to, children, className}) {
  const external = isExternal(to);
  return (
    <Link to={to} className={clsx(styles.textLink, className)}>
      {children}
      {external ? <ExternalMark size={14} /> : <Icon name="arrow-right" size={14} strokeWidth={2.2} />}
    </Link>
  );
}

export function Card({icon, title, children, className}) {
  return (
    <div className={clsx(styles.card, className)}>
      {icon && <Icon name={icon} size={22} strokeWidth={1.8} className={styles.cardIcon} />}
      <Heading as="h3" className={styles.cardTitle}>
        {title}
      </Heading>
      <p className={styles.cardText}>{children}</p>
    </div>
  );
}

/** A card that is one link from edge to edge. */
export function LinkCard({to, icon, title, children, className}) {
  return (
    <Link to={to} className={clsx(styles.card, styles.linkCard, className)}>
      {icon && <Icon name={icon} size={22} strokeWidth={1.8} className={styles.cardIcon} />}
      <span className={styles.cardTitle}>
        {title}
        {isExternal(to) && <ExternalMark size={13} />}
      </span>
      <span className={styles.cardText}>{children}</span>
    </Link>
  );
}

export function Grid({min = 220, className, children, as: Tag = 'div'}) {
  return (
    <Tag className={clsx(styles.grid, className)} style={{'--rf-grid-min': `${min}px`}}>
      {children}
    </Tag>
  );
}

export function CheckList({items, className}) {
  return (
    <ul className={clsx(styles.checkList, className)}>
      {items.map((item, index) => (
        <li key={index}>
          <Icon name="check" size={16} strokeWidth={2.2} />
          {item}
        </li>
      ))}
    </ul>
  );
}

/** Copies `text`, and says so in a live region rather than only by changing its label. */
export function CopyButton({text, label}) {
  const [state, setState] = useState('idle');
  const timer = useRef(null);
  useEffect(() => () => clearTimeout(timer.current), []);

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setState('copied');
    } catch {
      setState('failed');
    }
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setState('idle'), 2000);
  }

  return (
    <>
      <button type="button" className={styles.copyButton} onClick={copy} aria-label={label}>
        <Icon name={state === 'copied' ? 'check' : 'copy'} size={14} />
        <span aria-hidden="true">
          {state === 'copied' ? (
            <Translate id="ui.copy.copied">Copied</Translate>
          ) : (
            <Translate id="ui.copy.label">Copy</Translate>
          )}
        </span>
      </button>
      <span className={styles.visuallyHidden} role="status">
        {state === 'copied' && translate({id: 'ui.copy.status.copied', message: 'Copied to the clipboard'})}
        {state === 'failed' && translate({id: 'ui.copy.status.failed', message: 'Could not copy; select the text instead'})}
      </span>
    </>
  );
}

/** The quickstart terminal: window dots, a label, Copy, the commands, then follow-up rows. */
export function Terminal({label, code, codeLabel, copyLabel, footer, warning}) {
  return (
    <div className={styles.terminal}>
      <div className={styles.terminalHeader}>
        <span className={styles.dots} aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <span className={styles.terminalLabel}>
          <span aria-hidden="true">{label}</span>
          <CopyButton text={code} label={copyLabel} />
        </span>
      </div>
      <pre tabIndex={0} aria-label={codeLabel} className={styles.terminalCode}>
        <code>{code}</code>
      </pre>
      {footer && <div className={styles.terminalRow}>{footer}</div>}
      {warning && (
        <div className={clsx(styles.terminalRow, styles.terminalWarning)}>
          <Icon name="triangle-alert" size={18} />
          <p>{warning}</p>
        </div>
      )}
    </div>
  );
}

export function Steps({items, className}) {
  return (
    <ol className={clsx(styles.steps, className)}>
      {items.map((item, index) => (
        <li key={index} className={styles.step}>
          <span className={styles.stepNumber} aria-hidden="true">
            {index + 1}
          </span>
          <div>
            <Heading as="h3" className={styles.stepTitle}>
              {item.title}
            </Heading>
            <p className={styles.stepText}>{item.text}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}

function readTicks(storageKey) {
  try {
    const stored = JSON.parse(window.localStorage.getItem(storageKey) ?? '[]');
    return Array.isArray(stored) ? stored.filter((id) => typeof id === 'string') : [];
  } catch {
    return [];
  }
}

/**
 * Real checkboxes. Ticks are a convenience kept in this browser only: when storage is
 * blocked or empty, the list simply starts unticked.
 */
export function Checklist({storageKey, items}) {
  const idPrefix = useId();
  const [ticked, setTicked] = useState([]);
  useEffect(() => setTicked(readTicks(storageKey)), [storageKey]);

  function toggle(id, checked) {
    const next = checked ? [...new Set([...ticked, id])] : ticked.filter((value) => value !== id);
    setTicked(next);
    try {
      window.localStorage.setItem(storageKey, JSON.stringify(next));
    } catch {
      // Storage is unavailable; the ticks last until the page is left.
    }
  }

  return (
    <ul className={styles.checklist}>
      {items.map((item) => {
        const inputId = `${idPrefix}-${item.id}`;
        return (
          <li key={item.id}>
            <input
              id={inputId}
              type="checkbox"
              checked={ticked.includes(item.id)}
              onChange={(event) => toggle(item.id, event.target.checked)}
            />
            <label htmlFor={inputId}>
              <strong>{item.lead}</strong> {item.text}
            </label>
          </li>
        );
      })}
    </ul>
  );
}

/** Questions as native disclosure widgets; the first starts open. */
export function Faq({items}) {
  return (
    <div className={styles.faq}>
      {items.map((item, index) => (
        <details key={index} open={index === 0}>
          <summary>
            <span>{item.question}</span>
            <Icon name="plus" size={18} className={styles.faqIcon} />
          </summary>
          <p>{item.answer}</p>
        </details>
      ))}
    </div>
  );
}

/** The closing band of a page: copy beside its actions, or centred above them. */
export function CtaBand({title, id, children, actions, align}) {
  return (
    <section aria-labelledby={id} className={clsx(styles.ctaBand, styles.deep, align === 'center' && styles.ctaCenter)}>
      <Container className={styles.ctaInner}>
        <div className={styles.ctaCopy}>
          <Heading as="h2" id={id} className={styles.ctaTitle}>
            {title}
          </Heading>
          <p className={styles.body}>{children}</p>
        </div>
        <div className={styles.actions}>{actions}</div>
      </Container>
    </section>
  );
}

/** A neutral label: a source, a destination, a product area. */
export function Tag({children}) {
  return <span className={styles.tag}>{children}</span>;
}

/**
 * A rounded label that always carries its words; colour only repeats them. `tone` is a
 * change type (new, improved, fixed, security, breaking) or outline.
 */
export function Pill({tone = 'outline', children}) {
  return <span className={clsx(styles.pill, styles[`pill-${tone}`])}>{children}</span>;
}

/** A browser window that reserves the place of a product screenshot until one exists. */
/**
 * A picture of the application in a browser frame: an image, or the video passed as children.
 * The image keeps its own size so the page does not move while it loads.
 */
export function ScreenshotFrame({src, alt, width, height, caption, children}) {
  return (
    <figure className={styles.screenshot}>
      <div className={styles.dots} aria-hidden="true">
        <i />
        <i />
        <i />
      </div>
      <div className={styles.screenshotMedia}>
        {children ?? <img src={src} alt={alt} width={width} height={height} loading="lazy" decoding="async" />}
      </div>
      {caption && <figcaption>{caption}</figcaption>}
    </figure>
  );
}

export function Actions({children, className}) {
  return <div className={clsx(styles.actions, className)}>{children}</div>;
}

export function Display({id, children, className}) {
  return (
    <Heading as="h1" id={id} className={clsx(styles.display, className)}>
      {children}
    </Heading>
  );
}

export function Lead({children, className}) {
  return <p className={clsx(styles.lead, className)}>{children}</p>;
}

/**
 * The Markdown a paragraph from a guide or the changelog uses: `code`, **bold** and
 * [links](…). `resolveHref` turns a link target into an address on this site or GitHub.
 */
export function InlineMarkdown({text, resolveHref = (target) => target}) {
  const parts = [];
  let last = 0;
  for (const match of text.matchAll(/`([^`]+)`|\*\*(.+?)\*\*|\[([^\]]+)\]\(([^)]+)\)/g)) {
    parts.push(text.slice(last, match.index));
    if (match[1] !== undefined) {
      parts.push(<code key={match.index}>{match[1]}</code>);
    } else if (match[2] !== undefined) {
      parts.push(
        <strong key={match.index}>
          <InlineMarkdown text={match[2]} resolveHref={resolveHref} />
        </strong>,
      );
    } else {
      parts.push(
        <Link key={match.index} to={resolveHref(match[4])}>
          <InlineMarkdown text={match[3]} resolveHref={resolveHref} />
        </Link>,
      );
    }
    last = match.index + match[0].length;
  }
  parts.push(text.slice(last));
  return <>{parts}</>;
}

export function Mono({children}) {
  return <span className={styles.mono}>{children}</span>;
}
