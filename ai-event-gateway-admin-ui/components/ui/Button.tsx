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

export type ButtonProps = ButtonVisualProps & ButtonHTMLAttributes<HTMLButtonElement>;
export type AnchorButtonProps = ButtonVisualProps & AnchorHTMLAttributes<HTMLAnchorElement>;

const toneClassMap: Record<ButtonTone, string> = {
  primary: 'border-purple-600 bg-purple-600 text-white shadow-sm hover:bg-purple-700 focus:ring-purple-200',
  secondary: 'border-slate-200 bg-white text-slate-700 shadow-sm hover:bg-slate-50 focus:ring-slate-200',
  warning: 'border-amber-300 bg-amber-50 text-amber-800 shadow-sm hover:bg-amber-100 focus:ring-amber-200',
  danger: 'border-rose-300 bg-rose-50 text-rose-700 shadow-sm hover:bg-rose-100 focus:ring-rose-200',
  success: 'border-emerald-300 bg-emerald-50 text-emerald-800 shadow-sm hover:bg-emerald-100 focus:ring-emerald-200',
  ghost: 'border-transparent bg-transparent text-slate-600 hover:bg-slate-100 focus:ring-slate-200',
};

const sizeClassMap: Record<ButtonSize, string> = {
  xs: 'rounded-lg px-2.5 py-1.5 text-xs',
  sm: 'rounded-xl px-3 py-2 text-xs',
  md: 'rounded-xl px-4 py-2.5 text-sm',
};

export function buttonClassName({
  tone = 'secondary',
  size = 'sm',
  fullWidth = false,
  className = '',
}: Readonly<ButtonVisualProps & { className?: string }>): string {
  const widthClass = fullWidth ? 'w-full justify-center' : '';
  return `inline-flex items-center gap-2 border font-bold transition focus:outline-none focus:ring-2 disabled:cursor-not-allowed disabled:opacity-50 ${toneClassMap[tone]} ${sizeClassMap[size]} ${widthClass} ${className}`;
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
  ...props
}: Readonly<ButtonProps>) {
  return (
    <button type={type} className={buttonClassName({ tone, size, fullWidth, className })} {...props}>
      {leftIcon}
      <span>{children}</span>
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
      <span>{children}</span>
      {rightIcon}
    </a>
  );
}
