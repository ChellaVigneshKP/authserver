"use client";
import { TextField, type TextFieldProps } from "@mui/material";
import {
  Controller,
  type Control,
  type FieldPath,
  type FieldValues,
  type UseFormReturn,
} from "react-hook-form";
import type { ZodError } from "zod";
type Props<T extends FieldValues> = Omit<
  TextFieldProps,
  "name" | "value" | "defaultValue" | "inputRef" | "onChange" | "onBlur"
> & {
  control: Control<T>;
  name: FieldPath<T>;
  onValueChange?: (value: string) => void;
};
export function FormField<T extends FieldValues>({
  control,
  name,
  onValueChange,
  ...props
}: Props<T>) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field: { ref, ...field }, fieldState }) => (
        <TextField
          {...props}
          {...field}
          value={field.value ?? ""}
          inputRef={ref}
          onChange={(e) => {
            field.onChange(e);
            onValueChange?.(e.target.value);
          }}
          error={!!fieldState.error || props.error}
          helperText={fieldState.error?.message ?? props.helperText}
        />
      )}
    />
  );
}
export function validationErrors<T extends FieldValues>(
  form: UseFormReturn<T>,
  error: ZodError,
) {
  for (const issue of error.issues)
    if (typeof issue.path[0] === "string")
      form.setError(issue.path[0] as FieldPath<T>, {
        type: "validation",
        message: issue.message,
      });
}
