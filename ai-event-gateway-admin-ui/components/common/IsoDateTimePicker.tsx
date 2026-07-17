"use client";

import { dateTimeLocalValueToIso, isoToDateTimeLocalValue } from "@/lib/utils/isoDateTime";

interface IsoDateTimePickerProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  className?: string;
  inputClassName?: string;
  helperText?: string;
  clearLabel?: string;
}

export function IsoDateTimePicker({
  label = "Expires At",
  value,
  onChange,
  className = "space-y-1 text-sm",
  inputClassName = "w-full rounded-lg border px-3 py-2",
  helperText = "使用本機時區選擇日期與時間，送出時會轉成 ISO UTC 格式。留空代表不設定到期日。",
  clearLabel = "Clear",
}: Readonly<IsoDateTimePickerProps>) {
  const localValue = isoToDateTimeLocalValue(value);

  return (
    <label className={className}>
      <span className="font-medium text-slate-700">{label}</span>
      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          type="datetime-local"
          step="1"
          className={`${inputClassName} flex-1`}
          value={localValue}
          onChange={(event) => onChange(dateTimeLocalValueToIso(event.target.value))}
        />
        <button
          type="button"
          onClick={() => onChange("")}
          className="shrink-0 rounded-lg border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
        >
          {clearLabel}
        </button>
      </div>
      {helperText ? <p className="text-xs font-normal leading-relaxed text-slate-500">{helperText}</p> : null}
      {value ? <p className="text-xs font-mono font-normal text-slate-500">Submit value: {value}</p> : null}
    </label>
  );
}
