export type AccountCreationMode = 'INVITATION' | 'ADMIN_CREATED';

export interface AccountCreationValue {
  username: string;
  displayName: string;
  email: string;
  creationMode: AccountCreationMode;
}
