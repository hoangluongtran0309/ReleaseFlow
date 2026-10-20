// One click always changes the theme on screen. The stock toggle also cycles through
// "system", which looks exactly like light or dark, so a click there seemed to do nothing.
// A first visit starts in the dark theme until the reader chooses.

import {useColorMode, useThemeConfig} from '@docusaurus/theme-common';
import ColorModeToggle from '@theme/ColorModeToggle';

export default function NavbarColorModeToggle({className}) {
  const {disableSwitch} = useThemeConfig().colorMode;
  const {colorMode, setColorMode} = useColorMode();
  if (disableSwitch) {
    return null;
  }
  return (
    <ColorModeToggle
      className={className}
      respectPrefersColorScheme={false}
      value={colorMode}
      onChange={setColorMode}
    />
  );
}
