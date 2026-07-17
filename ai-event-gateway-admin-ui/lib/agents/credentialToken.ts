const DEFAULT_TOKEN_BYTES = 32;
const TOKEN_PREFIX = "agt";

function assertByteLength(byteLength: number): void {
  if (!Number.isInteger(byteLength) || byteLength < 16 || byteLength > 128) {
    throw new Error("Token byte length must be an integer between 16 and 128 bytes.");
  }
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function fillSecureRandomBytes(bytes: Uint8Array): void {
  const cryptoProvider = globalThis.crypto;
  if (cryptoProvider?.getRandomValues) {
    cryptoProvider.getRandomValues(bytes);
    return;
  }

  throw new Error("Secure random generator is not available in this browser/runtime.");
}

export function generateAgentCredentialToken(byteLength = DEFAULT_TOKEN_BYTES): string {
  assertByteLength(byteLength);
  const bytes = new Uint8Array(byteLength);
  fillSecureRandomBytes(bytes);
  return `${TOKEN_PREFIX}_${toHex(bytes)}`;
}

export function isGeneratedAgentCredentialToken(token: string): boolean {
  return /^agt_[0-9a-f]{32,256}$/i.test(token.trim());
}
