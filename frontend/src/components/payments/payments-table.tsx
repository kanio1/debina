"use client";

import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from "@tanstack/react-table";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ScreenStateContent } from "@/components/shared/screen-state";

export interface PaymentRow {
  id: string;
  endToEndId: string;
  amount: number;
  currency: string;
  status: string;
}

const columns: ColumnDef<PaymentRow>[] = [
  {
    accessorKey: "endToEndId",
    header: "End-to-End ID",
    cell: ({ row }) => (
      <a href={`/payments/${row.original.id}`} className="underline-offset-2 hover:underline">
        {row.original.endToEndId}
      </a>
    ),
  },
  { accessorKey: "amount", header: "Amount" },
  { accessorKey: "currency", header: "Currency" },
  { accessorKey: "status", header: "Status" },
];

export type PaymentsTableStatus = "loading" | "error" | "ready";

interface PaymentsTableProps {
  status: PaymentsTableStatus;
  payments: PaymentRow[];
  errorMessage?: string;
}

export function PaymentsTable({ status, payments, errorMessage }: PaymentsTableProps) {
  const table = useReactTable({
    data: payments,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <Table data-testid="payments.list.table">
      <TableHeader>
        {table.getHeaderGroups().map((headerGroup) => (
          <TableRow key={headerGroup.id}>
            {headerGroup.headers.map((header) => (
              <TableHead key={header.id} scope="col">
                {header.isPlaceholder
                  ? null
                  : flexRender(header.column.columnDef.header, header.getContext())}
              </TableHead>
            ))}
          </TableRow>
        ))}
      </TableHeader>
      <TableBody>
        {status === "loading" && (
          <TableRow>
            <TableCell colSpan={columns.length}>
              <ScreenStateContent kind="loading" testIdPrefix="payments.list" message="Loading payments…" />
            </TableCell>
          </TableRow>
        )}
        {status === "error" && (
          <TableRow>
            <TableCell colSpan={columns.length}>
              <ScreenStateContent
                kind="error"
                testIdPrefix="payments.list"
                message={errorMessage ?? "Could not load payments."}
              />
            </TableCell>
          </TableRow>
        )}
        {status === "ready" && payments.length === 0 && (
          <TableRow>
            <TableCell colSpan={columns.length}>
              <ScreenStateContent kind="empty" testIdPrefix="payments.list" message="No payments submitted yet." />
            </TableCell>
          </TableRow>
        )}
        {status === "ready" &&
          table.getRowModel().rows.map((row) => (
            <TableRow key={row.id} data-testid="payments.list.row">
              {row.getVisibleCells().map((cell) => (
                <TableCell key={cell.id}>
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </TableCell>
              ))}
            </TableRow>
          ))}
      </TableBody>
    </Table>
  );
}
