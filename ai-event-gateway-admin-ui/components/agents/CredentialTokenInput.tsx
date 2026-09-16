"use client";

import { generateAgentCredentialToken } from "@/lib/agents/credentialToken";

interface CredentialTokenInputProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  placeholder?: string;
  className?: string;
  inputClassName?: string;
  helperText?: string;
  onGenerateError?: (message: string) => void;
}

export function CredentialTokenInput({
  label = "Credential Token",
  value,
  onChange,
  required = false,
  placeholder = "plain token; Core stores only hash",
  className = "space-y-1 text-sm md:col-span-2",
  inputClassName = "w-full rounded-lg border px-3 py-2",
  helperText = "Generate Token  Web Crypto  256-bit token;Core Save hashReview the configuration and try again. Agent Configuration",
  onGenerateError,
}: Readonly<CredentialTokenInputProps>) {
  function generateToken() {
    try {
      onChange(generateAgentCredentialToken());
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      onGenerateError?.(message);
    }
  }

  return (
    <label className={className}>
      <span className="font-medium text-slate-700">
        {label} {required ? "*" : ""}
      </span>
      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          className={`${inputClassName} flex-1`}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          autoComplete="off"
        />
        <button
          type="button"
          onClick={generateToken}
          className="shrink-0 rounded-lg border border-emerald-200 px-3 py-2 text-xs font-semibold text-emerald-700 hover:bg-emerald-50"
          title="Generate a secure random Agent credential token"
        >
          Generate Token
        </button>
      </div>
      {helperText ? <p className="text-xs font-normal leading-relaxed text-slate-500">{helperText}</p> : null}
    </label>
  );
}
