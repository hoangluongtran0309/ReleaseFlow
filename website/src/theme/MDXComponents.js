// A docs article's H1 comes from its Markdown; the meta row (updated date, edit link) is
// placed right after it. Outside a docs article the row renders nothing.

import MDXComponents from '@theme-original/MDXComponents';
import MDXHeading from '@theme/MDXComponents/Heading';
import {DocMeta} from '@site/src/components/docs/DocParts';

function DocTitle(props) {
  return (
    <>
      <MDXHeading as="h1" {...props} />
      <DocMeta />
    </>
  );
}

export default {...MDXComponents, h1: DocTitle};
