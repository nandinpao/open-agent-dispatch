export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly detail?: unknown,
    public readonly code?: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
