"use client";
import { ConfirmAction } from "../../components/ConfirmAction";
export function HostedActionLink({
  action,
  returnTo,
  label,
}: {
  action: "change-profile" | "change-password" | "recovery";
  returnTo: "/account" | "/account/security";
  label: string;
}) {
  return (
    <ConfirmAction
      label={label}
      title="Continue to the identity service"
      description="This action opens the identity service in this tab. Your account information is managed there."
      onConfirm={() =>
        location.assign(
          "/account/action?" + new URLSearchParams({ action, returnTo }),
        )
      }
    />
  );
}
