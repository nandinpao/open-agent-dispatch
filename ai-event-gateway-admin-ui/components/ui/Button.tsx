import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonTone = 'primary' | 'secondary' | 'warning' | 'danger' | 'success' | 'ghost';
export type ButtonSize = 'xs' | 'sm' | 'md';

export interface ButtonVisualProps {
  tone?: ButtonTone;
  size?: ButtonSize;
  fullWidth?: boolean;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
}

export interface ButtonBehaviorProps {
  busy?: boolean;
  busyLabel?: string;
}

export type ButtonProps = ButtonVisualProps & ButtonBehaviorProps & ButtonHTMLAttributes<HTMLButtonElement>;
export type AnchorButtonProps = ButtonVisualProps & AnchorHTMLAttributes<HTMLAnchorElement>;

const toneClassMap: Record<ButtonTone, string> = {
  primary: 'border-blue-700 bg-blue-700 text-white shadow-sm hover:bg-blue-800 focus-visible:ring-blue-300',
  secondary: 'border-slate-300 bg-white text-slate-800 shadow-sm hover:bg-slate-50 focus-visible:ring-slate-300',
  warning: 'border-amber-500 bg-amber-50 text-amber-900 shadow-sm hover:bg-amber-100 focus-visible:ring-amber-300',
  danger: 'border-rose-600 bg-rose-600 text-white shadow-sm hover:bg-rose-700 focus-visible:ring-rose-300',
  success: 'border-emerald-700 bg-emerald-700 text-white shadow-sm hover:bg-emerald-800 focus-visible:ring-emerald-300',
  ghost: 'border-transparent bg-transparent text-slate-700 hover:bg-slate-100 focus-visible:ring-slate-300',
};

const sizeClassMap: Record<ButtonSize, string> = {
  xs: 'min-h-8 rounded-lg px-2.5 py-1.5 text-xs',
  sm: 'min-h-9 rounded-xl px-3 py-2 text-xs',
  md: 'min-h-10 rounded-xl px-4 py-2.5 text-sm',
};

export function buttonClassName({
  tone = 'secondary',
  size = 'sm',
  fullWidth = false,
  className = '',
}: Readonly<ButtonVisualProps & { className?: string }>): string {
  const widthClass = fullWidth ? 'w-full justify-center' : '';
  return `inline-flex items-center justify-center gap-2 border font-bold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${toneClassMap[tone]} ${sizeClassMap[size]} ${widthClass} ${className}`;
}

export function Button({
  tone = 'secondary',
  size = 'sm',
  fullWidth = false,
  leftIcon,
  rightIcon,
  className = '',
  children,
  type = 'button',
  busy = false,
  busyLabel = 'Working…',
  disabled,
  ...props
}: Readonly<ButtonProps>) {
  return (
    <button
      type={type}
      aria-busy={busy || undefined}
      disabled={disabled || busy}
      className={buttonClassName({ tone, size, fullWidth, className })}
      {...props}
    >
      {leftIcon}
      {busy ? busyLabel : children}
      {rightIcon}
    </button>
  );
}

export function AnchorButton({
  tone = 'secondary',
  size = 'sm',
  fullWidth = false,
  leftIcon,
  rightIcon,
  className = '',
  children,
  ...props
}: Readonly<AnchorButtonProps>) {
  return (
    <a className={buttonClassName({ tone, size, fullWidth, className })} {...props}>
      {leftIcon}
      {children}
      {rightIcon}
    </a>
  );
}
