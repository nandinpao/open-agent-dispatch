'use client';

import Script from 'next/script';
import { useCallback, useEffect, useRef, useState } from 'react';

type QrCorrectLevel = { L: number; M: number; Q: number; H: number };
type QrCodeOptions = {
  text: string;
  width: number;
  height: number;
  colorDark: string;
  colorLight: string;
  correctLevel: number;
};
type QrCodeConstructor = {
  new (element: HTMLElement, options: QrCodeOptions): { clear(): void; makeCode(value: string): void };
  CorrectLevel: QrCorrectLevel;
};

declare global {
  interface Window {
    QRCode?: QrCodeConstructor;
  }
}

export function TotpQrCode({ value, label }: Readonly<{ value: string; label: string }>) {
  const targetRef = useRef<HTMLDivElement>(null);
  const [scriptReady, setScriptReady] = useState(false);
  const [renderError, setRenderError] = useState('');

  const renderQrCode = useCallback(() => {
    const target = targetRef.current;
    const QRCode = window.QRCode;
    if (!target || !QRCode || !value) return;

    target.replaceChildren();
    try {
      new QRCode(target, {
        text: value,
        width: 232,
        height: 232,
        colorDark: '#0f172a',
        colorLight: '#ffffff',
        correctLevel: QRCode.CorrectLevel.M,
      });
      setRenderError('');
    } catch (reason) {
      setRenderError(reason instanceof Error ? reason.message : 'Unable to render the authenticator QR code.');
    }
  }, [value]);

  useEffect(() => {
    if (scriptReady || window.QRCode) renderQrCode();
  }, [renderQrCode, scriptReady]);

  return (
    <div className="space-y-3">
      <Script
        src="/vendor/qrcodejs/qrcode.min.js"
        strategy="afterInteractive"
        onLoad={() => setScriptReady(true)}
        onError={() => setRenderError('Unable to load the local QR code renderer.')}
      />
      <div className="mx-auto w-fit rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div
          ref={targetRef}
          role="img"
          aria-label={`QR code for ${label}`}
          className="min-h-[232px] min-w-[232px] [&>canvas]:block [&>img]:block"
        />
      </div>
      {renderError ? <p role="alert" className="text-sm font-semibold text-rose-700">{renderError}</p> : null}
    </div>
  );
}
