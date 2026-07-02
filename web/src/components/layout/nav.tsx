import {
  Database,
  Layers,
  GaugeCircle,
  Upload,
  History,
  HeartPulse,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  description: string;
}

/**
 * Single source of truth for navigation. To add a screen: create the feature
 * module + route, then append one entry here — the sidebar renders from this
 * array automatically. See EXTENDING.md.
 */
export const NAV_ITEMS: NavItem[] = [
  {
    href: "/workspace",
    label: "Query Workspace",
    icon: Database,
    description: "Compose queries and visualize results",
  },
  {
    href: "/schemas",
    label: "Schema Explorer",
    icon: Layers,
    description: "Browse dimensions, levels, and hierarchies",
  },
  {
    href: "/performance",
    label: "Performance",
    icon: GaugeCircle,
    description: "Query timing and cache analytics",
  },
  {
    href: "/ingest",
    label: "Ingestion",
    icon: Upload,
    description: "Upload source files and track ingestion",
  },
  {
    href: "/history",
    label: "History",
    icon: History,
    description: "Past queries and re-run",
  },
  {
    href: "/health",
    label: "Health",
    icon: HeartPulse,
    description: "Service status",
  },
];
