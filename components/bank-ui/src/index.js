// Public entry point of the @queenswood/bank-ui design system.
//
//   import {
//     Logo, Wordmark, AppNav, ThemeToggle,
//     Button, Badge,
//     Sidenav, SidenavGroup, SidenavItem,
//     PageHeader, Drawer,
//     Table, Thead, Tbody, Tr, Th, Td,
//     Expander, MoneyCell, Phase,        // ← ledger tree-table
//     Field, Input, Select,
//     Card, CardHeader, CardBody, CardFooter, CodeCard,
//   } from "@queenswood/bank-ui";
//   import { formatMoney, moneyTone, sumMinor } from "@queenswood/bank-ui";
//   import { bootstrapTheme, setTheme, themeState, resolvedTheme }
//     from "@queenswood/bank-ui";
//   import "@queenswood/bank-ui/tokens.css";

export { default as Logo } from "./Logo.svelte";
export { default as Wordmark } from "./Wordmark.svelte";
export { default as AppNav } from "./AppNav.svelte";
export { default as ThemeToggle } from "./ThemeToggle.svelte";

export { default as Button } from "./Button.svelte";
export { default as Badge } from "./Badge.svelte";

export { default as Sidenav } from "./Sidenav.svelte";
export { default as SidenavGroup } from "./SidenavGroup.svelte";
export { default as SidenavItem } from "./SidenavItem.svelte";

export { default as PageHeader } from "./PageHeader.svelte";
export { default as Drawer } from "./Drawer.svelte";

export { default as Table } from "./Table.svelte";
export { default as Thead } from "./Thead.svelte";
export { default as Tbody } from "./Tbody.svelte";
export { default as Tr } from "./Tr.svelte";
export { default as Th } from "./Th.svelte";
export { default as Td } from "./Td.svelte";

// Tree-table additions (ledger accounts → constituent balances).
export { default as Expander } from "./Expander.svelte";
export { default as MoneyCell } from "./MoneyCell.svelte";
export { default as Phase } from "./Phase.svelte";
export { formatMoney, moneyTone, sumMinor, CCY_SYMBOLS } from "./money.js";

// Policy matrix — atoms + the composed per-domain table, plus the
// view-model helpers (DOMAINS / grouping) and bound formatting.
export { default as Effect } from "./Effect.svelte";
export { default as Bound } from "./Bound.svelte";
export { default as Improving } from "./Improving.svelte";
export { default as FilterChips } from "./FilterChips.svelte";
export { default as PolicyMatrix } from "./PolicyMatrix.svelte";
export {
  DOMAINS,
  DOMAIN_ORDER,
  GROUP_ORDER,
  CATEGORY_TONE,
  groupByDomain,
  sectionRows,
} from "./policy.js";
// CCY_SYMBOLS already exported from money.js (same constant); don't re-export.
export { formatAggregate, boundOp, boundWindow, WINDOW_LABEL } from "./bounds.js";

export { default as Field } from "./Field.svelte";
export { default as Input } from "./Input.svelte";
export { default as Select } from "./Select.svelte";

export { default as Card } from "./Card.svelte";
export { default as CardHeader } from "./CardHeader.svelte";
export { default as CardBody } from "./CardBody.svelte";
export { default as CardFooter } from "./CardFooter.svelte";
export { default as CodeCard } from "./CodeCard.svelte";

export {
  themeState,
  setTheme,
  bootstrapTheme,
  resolvedTheme,
} from "./theme.svelte.js";
