// A wide table scrolls sideways on a narrow screen. A keyboard can scroll it only when it can
// take focus, so every table in a guide is put in the tab order.

export default function rehypeFocusableTables() {
  return (tree) => {
    const visit = (node) => {
      if (node.type === 'element' && node.tagName === 'table') {
        node.properties = {...node.properties, tabIndex: 0};
      }
      node.children?.forEach(visit);
    };
    visit(tree);
  };
}
