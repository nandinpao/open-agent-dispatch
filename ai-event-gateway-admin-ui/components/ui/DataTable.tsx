import type { ReactNode } from 'react';
import { EmptyState } from './EmptyState';

export type TableDensity = 'compact' | 'cozy' | 'comfortable';

export interface TableDensityClasses {
  headCell: string;
  bodyCell: string;
  row: string;
}

export const tableDensityClasses: Record<TableDensity, TableDensityClasses> = {
  compact: {
    headCell: 'px-3 py-2',
    bodyCell: 'px-3 py-2',
    row: 'text-xs',
  },
  cozy: {
    headCell: 'px-4 py-3',
    bodyCell: 'px-4 py-3',
    row: 'text-sm',
  },
  comfortable: {
    headCell: 'px-5 py-4',
    bodyCell: 'px-5 py-4',
    row: 'text-sm',
  },
};

export interface DataTableShellProps {
  children: ReactNode;
  className?: string;
}

export function DataTableShell({ children, className = '' }: Readonly<DataTableShellProps>) {
  return <div className={`overflow-x-auto ${className}`}>{children}</div>;
}

export interface TableEmptyRowProps {
  colSpan: number;
  title: string;
  description?: ReactNode;
  nextAction?: ReactNode;
  density?: TableDensity;
}

export function TableEmptyRow({ colSpan, title, description, nextAction, density = 'cozy' }: Readonly<TableEmptyRowProps>) {
  const classes = tableDensityClasses[density];
  return (
    <tr>
      <td colSpan={colSpan} className={classes.bodyCell}>
        <EmptyState title={title} description={description} nextAction={nextAction} compact tone="info" />
      </td>
    </tr>
  );
}
