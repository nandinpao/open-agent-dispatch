'use client';

import { useCallback, useEffect, useState } from 'react';
import { ADMIN_UI_MODE_STORAGE_KEY, normalizeAdminUiMode, type AdminUiMode } from '@/lib/navigation/adminUiMode';

const ADMIN_UI_MODE_EVENT = 'opendispatch:admin-ui-mode-change';

type AdminUiModeEventDetail = {
  mode: AdminUiMode;
};

function readStoredMode(): AdminUiMode {
  if (typeof window === 'undefined') {
    return 'basic';
  }
  return normalizeAdminUiMode(window.localStorage.getItem(ADMIN_UI_MODE_STORAGE_KEY));
}

export function useAdminUiMode() {
  const [mode, setModeState] = useState<AdminUiMode>('basic');

  useEffect(() => {
    setModeState(readStoredMode());

    const handleStorage = (event: StorageEvent) => {
      if (event.key === ADMIN_UI_MODE_STORAGE_KEY) {
        setModeState(normalizeAdminUiMode(event.newValue));
      }
    };

    const handleModeChange = (event: Event) => {
      const customEvent = event as CustomEvent<AdminUiModeEventDetail>;
      setModeState(normalizeAdminUiMode(customEvent.detail?.mode));
    };

    window.addEventListener('storage', handleStorage);
    window.addEventListener(ADMIN_UI_MODE_EVENT, handleModeChange);
    return () => {
      window.removeEventListener('storage', handleStorage);
      window.removeEventListener(ADMIN_UI_MODE_EVENT, handleModeChange);
    };
  }, []);

  const setMode = useCallback((nextMode: AdminUiMode) => {
    const normalized = normalizeAdminUiMode(nextMode);
    setModeState(normalized);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(ADMIN_UI_MODE_STORAGE_KEY, normalized);
      window.dispatchEvent(new CustomEvent<AdminUiModeEventDetail>(ADMIN_UI_MODE_EVENT, { detail: { mode: normalized } }));
    }
  }, []);

  return { mode, setMode };
}
