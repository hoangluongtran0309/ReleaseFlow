// A code block with a header row: its language (or title) on the left, Copy on the right.

import {useCodeBlockContext} from '@docusaurus/theme-common/internal';
import {translate} from '@docusaurus/Translate';
import Container from '@theme/CodeBlock/Container';
import Content from '@theme/CodeBlock/Content';
import {CopyButton} from '@site/src/components/ui';
import styles from './styles.module.css';

export default function CodeBlockLayout({className}) {
  const {metadata} = useCodeBlockContext();
  return (
    <Container as="div" className={[className, metadata.className].filter(Boolean).join(' ')}>
      <div className={styles.header}>
        <span className={styles.language}>{metadata.title ?? metadata.language ?? 'text'}</span>
        <CopyButton text={metadata.code} label={translate({id: 'theme.CodeBlock.copyButtonAriaLabel', message: 'Copy code to clipboard'})} />
      </div>
      <div className={styles.content}>
        <Content />
      </div>
    </Container>
  );
}
