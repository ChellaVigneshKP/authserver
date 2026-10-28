import { it, expect } from "vitest";
import { createElement } from "react";
import { renderToString } from "react-dom/server";
import { useForm } from "react-hook-form";
import { FormField } from "../../src/features/clients/FormField";
it("RHF MUI adapter renders authoritative defaults on the actual labeled input, including selects", () => {
  function Form() {
    const { control } = useForm<{ name: string; method: string }>({
      defaultValues: { name: "Saved client", method: "POST" },
    });
    return createElement(
      "form",
      null,
      createElement(FormField<{ name: string; method: string }>, {
        control,
        name: "name",
        label: "Client name",
      }),
      createElement(
        FormField<{ name: string; method: string }>,
        {
          control,
          name: "method",
          label: "Method",
          select: true,
          slotProps: { select: { native: true } },
        },
        createElement("option", { value: "GET" }, "GET"),
        createElement("option", { value: "POST" }, "POST"),
      ),
    );
  }
  const html = renderToString(createElement(Form));
  expect(html.includes('value="Saved client"')).toBe(true);
  expect(html.includes('name="name"')).toBe(true);
  expect(
    /\<option[^>]*(?:value="POST"[^>]*selected=""|selected=""[^>]*value="POST")/.test(
      html,
    ),
  ).toBe(true);
});
