import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
} from "@mui/material";
export function DataTable<T>({
  caption,
  rows,
  columns,
  rowKey,
}: {
  caption: string;
  rows: T[];
  columns: {
    key: string;
    label: string;
    render: (row: T) => React.ReactNode;
  }[];
  rowKey: (row: T) => string;
}) {
  return (
    <TableContainer tabIndex={0} role="region" aria-label={caption}>
      <Table>
        <caption>{caption}</caption>
        <TableHead>
          <TableRow>
            {columns.map((c) => (
              <TableCell key={c.key} scope="col">
                {c.label}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={rowKey(row)}>
              {columns.map((c, i) => (
                <TableCell
                  key={c.key}
                  component={i === 0 ? "th" : "td"}
                  scope={i === 0 ? "row" : undefined}
                >
                  {c.render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
