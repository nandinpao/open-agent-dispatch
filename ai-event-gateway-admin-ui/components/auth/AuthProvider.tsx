'use client';

import type { ReactNode } from 'react';
import { createContext,useCallback,useContext,useEffect,useMemo,useRef,useState } from 'react';
import { usePathname,useRouter } from 'next/navigation';
import { iamAuthApi } from '@/lib/api/iamAuthApi';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { ApiError } from '@/lib/api/errors';
import { setCoreTenantContext } from '@/lib/api/coreClient';
import { UNAUTHORIZED_EVENT,clearAuthSession } from '@/lib/auth/session';
import { readStoredRootAdministrationTenant, writeStoredRootAdministrationTenant } from '@/lib/auth/workspaceTenantContext';
import { getPublicEnv } from '@/lib/constants/env';
import type { AdminTenantOption,AdminUser,LoginRequest } from '@/lib/types/admin';
import type { IamLoginResponse, MfaEnrollment } from '@/lib/iam/types';

export type AuthStatus='CHECKING'|'AUTHENTICATED'|'UNAUTHENTICATED'|'SERVICE_UNAVAILABLE';
export interface AuthContextValue {
  status:AuthStatus;
  user:AdminUser|null;
  tenants:AdminTenantOption[];
  /** Home Tenant fixed by the authenticated Human Session. Empty for INSTANCE_ROOT. */
  selectedTenantId:string;
  /** Effective Tenant used by every tenant-scoped UI/API request. */
  activeTenantId:string;
  /** Instance Root administration scope; independent from the root Session tenantChoices. */
  administrationTenantId:string;
  setAdministrationTenantId:(tenantId:string)=>void;
  pendingLogin:IamLoginResponse|null;
  login:(payload:LoginRequest)=>Promise<IamLoginResponse|null>;
  verifyMfa:(code:string,recoveryCode?:boolean)=>Promise<void>;
  changePassword:(currentPassword:string,newPassword:string)=>Promise<void>;
  forgotPassword:(username:string)=>Promise<void>;
  resetPassword:(token:string,newPassword:string)=>Promise<void>;
  activateInvitation:(token:string,newPassword:string)=>Promise<void>;
  beginMfaEnrollment:(accountLabel:string,issuer:string)=>Promise<MfaEnrollment>;
  confirmMfaEnrollment:(methodId:string,code:string,acknowledgeRecoveryCodesSaved:boolean,expectedVersion:number)=>Promise<void>;
  logout:()=>Promise<void>;
  refreshCurrentUser:()=>Promise<void>;
  hasPermission:(...permissions:string[])=>boolean;
}

const AuthContext=createContext<AuthContextValue|null>(null);
const mockAdminUser:AdminUser={userId:'mock-admin',username:'mock-admin',displayName:'Mock Administrator',roles:['ADMIN'],permissions:['*'],allowedTenantIds:['mock-tenant'],selectedTenantId:'mock-tenant',authenticationMethods:['MOCK']};
const mockTenants:AdminTenantOption[]=[{tenantId:'mock-tenant',selected:true,tenantName:'Mock Tenant',membershipStatus:'ACTIVE',roleSummary:['ADMIN']}];
const PUBLIC_ROUTES=['/login','/forgot-password','/reset-password','/activate-account'];
function tenantOptions(user:AdminUser):AdminTenantOption[]{
  if(user.tenantChoices?.length)return user.tenantChoices.map(t=>({tenantId:t.tenantId,tenantCode:t.tenantCode,tenantName:t.tenantName,membershipStatus:t.membershipStatus,roleSummary:t.roleSummary,selected:t.tenantId===user.selectedTenantId}));
  return (user.allowedTenantIds??[]).map(id=>({tenantId:id,selected:id===user.selectedTenantId}));
}
function requiredRoute(user:AdminUser):string|null{
  const actions=user.requiredActions??[];
  if(actions.includes('CHANGE_PASSWORD'))return '/change-password';
  if(actions.includes('ENROLL_MFA'))return '/mfa-enrollment';
  if(actions.includes('COMPLETE_BOOTSTRAP'))return '/setup';
  return null;
}
function authenticatedHome():string{
  // Resolve the first authorized workspace route only after the backend entitlement projection loads.
  // This prevents administration-only Responsibilities from being sent to an unauthorized Dashboard.
  return '/home';
}

export function useAuth(){const context=useContext(AuthContext);if(!context)throw new Error('useAuth must be used inside AuthProvider.');return context;}

export function AuthProvider({children}:{children:ReactNode}){
  const env=getPublicEnv();
  const router=useRouter();
  const pathname=usePathname();
  const bypass=!env.authEnabled||env.useMock;
  const [status,setStatus]=useState<AuthStatus>(bypass?'AUTHENTICATED':'CHECKING');
  const [user,setUser]=useState<AdminUser|null>(bypass?mockAdminUser:null);
  const [tenants,setTenants]=useState<AdminTenantOption[]>(bypass?mockTenants:[]);
  const [pendingLogin,setPendingLogin]=useState<IamLoginResponse|null>(null);
  const [administrationTenantId,setAdministrationTenantIdState]=useState<string>(bypass?mockAdminUser.selectedTenantId??'':'');
  const unauthorizedValidationInFlight=useRef(false);
  const sessionRefreshInFlight=useRef<Promise<void>|null>(null);
  const lastUnauthorizedValidationAt=useRef(0);
  const statusRef=useRef<AuthStatus>(status);

  const apply=useCallback((next:AdminUser)=>{
    const options=tenantOptions(next);
    const instanceRoot=next.roles.includes('INSTANCE_ROOT');
    const rootAdministrationTenant=instanceRoot?readStoredRootAdministrationTenant():'';
    // Establish the Core transport Tenant synchronously with the authenticated Session. Child
    // polling effects may run before AuthProvider's synchronization effect on the next render;
    // they must never emit an unscoped tenant-authorized request during that window.
    setCoreTenantContext(instanceRoot?rootAdministrationTenant:(next.selectedTenantId??''));
    setUser(next);setTenants(options);setStatus('AUTHENTICATED');
    setAdministrationTenantIdState(current=>{
      if(!instanceRoot)return next.selectedTenantId??'';
      // INSTANCE_ROOT tenantChoices describe Session membership choices, not the global
      // administration Tenant directory. Never erase a valid Root administration scope merely
      // because /api/session correctly returns tenantChoices=[].
      return current||rootAdministrationTenant;
    });
  },[]);
  const navigateAfterAuthentication=useCallback((next:AdminUser)=>{
    router.replace(requiredRoute(next)??authenticatedHome());
  },[router]);
  const refreshCurrentUser=useCallback(()=>{
    if(bypass){apply(mockAdminUser);return Promise.resolve();}
    if(sessionRefreshInFlight.current)return sessionRefreshInFlight.current;
    const request=iamAuthApi.uiSession()
      .then(next=>{apply(next);})
      .catch(error=>{
        const sessionRejected=error instanceof ApiError&&error.status===401;
        if(!sessionRejected){
          // A transient authorization or Session repository failure is not a logout. Preserve an
          // already authenticated workspace, or show a retryable service state during first load.
          if(statusRef.current!=='AUTHENTICATED')setStatus('SERVICE_UNAVAILABLE');
          return;
        }
        writeStoredRootAdministrationTenant('');clearAuthSession();setUser(null);setTenants([]);setAdministrationTenantIdState('');setCoreTenantContext('');setStatus('UNAUTHENTICATED');
      })
      .finally(()=>{sessionRefreshInFlight.current=null;});
    sessionRefreshInFlight.current=request;
    return request;
  },[apply,bypass]);

  const login=useCallback(async(payload:LoginRequest)=>{
    const flow=await iamAuthApi.login(payload);
    if(flow.state==='TENANT_SELECTION_REQUIRED'){
      throw new Error('The server requested Tenant selection, but interactive Tenant selection is no longer supported. Configure a Default Tenant for this Person and deploy the current authentication runtime.');
    }
    if(flow.state==='MFA_REQUIRED'||flow.challengeId){setPendingLogin(flow);return flow;}
    const next=await iamAuthApi.uiSession();
    apply(next);setPendingLogin(null);navigateAfterAuthentication(next);return flow;
  },[apply,navigateAfterAuthentication]);

  const verifyMfa=useCallback(async(code:string,recoveryCode=false)=>{
    if(!pendingLogin?.challengeId)throw new Error('MFA challenge is not active.');
    try{
      await iamAuthApi.verifyMfa(pendingLogin.challengeId,code,recoveryCode);
    }catch(error){
      const message=error instanceof Error?error.message:'';
      const challengeInvalid=error instanceof ApiError
        ? error.code==='AUTH_LOGIN_CHALLENGE_INVALID'
        : message.includes('AUTH_LOGIN_CHALLENGE_INVALID');
      if(challengeInvalid){
        setPendingLogin(null);
        throw new Error('The sign-in challenge expired or was already used. Sign in again to request a new challenge.');
      }
      throw error;
    }
    const next=await iamAuthApi.uiSession();apply(next);setPendingLogin(null);navigateAfterAuthentication(next);
  },[apply,navigateAfterAuthentication,pendingLogin]);

  const changePassword=useCallback(async(currentPassword:string,newPassword:string)=>{
    if(user?.credentialVersion===undefined)throw new Error('Credential version is unavailable. Sign in again.');
    await iamAuthApi.changePassword(currentPassword,newPassword,user.credentialVersion);
    clearAuthSession('password-changed');setUser(null);setTenants([]);setPendingLogin(null);setStatus('UNAUTHENTICATED');
    router.replace('/login?passwordChanged=1');
  },[router,user?.credentialVersion]);

  const forgotPassword=useCallback((username:string)=>iamAuthApi.forgot(username),[]);
  const resetPassword=useCallback((token:string,newPassword:string)=>iamAuthApi.reset(token,newPassword),[]);
  const activateInvitation=useCallback((token:string,newPassword:string)=>iamAuthApi.activateInvitation(token,newPassword),[]);
  const beginMfaEnrollment=useCallback((accountLabel:string,issuer:string)=>iamAuthApi.beginMfaEnrollment(accountLabel,issuer),[]);
  const confirmMfaEnrollment=useCallback(async(methodId:string,code:string,acknowledgeRecoveryCodesSaved:boolean,expectedVersion:number)=>{
    await iamAuthApi.confirmMfaEnrollment(methodId,code,acknowledgeRecoveryCodesSaved,expectedVersion);
    clearAuthSession('logout');setUser(null);setTenants([]);setPendingLogin(null);setStatus('UNAUTHENTICATED');
    router.replace('/login?mfaEnrolled=1');
  },[router]);
  const logout=useCallback(async()=>{
    try{if(!bypass)await iamAuthApi.logout();}
    catch{}finally{writeStoredRootAdministrationTenant('');clearAuthSession('logout');setUser(bypass?mockAdminUser:null);setTenants(bypass?mockTenants:[]);setAdministrationTenantIdState(bypass?mockAdminUser.selectedTenantId??'':'');setStatus(bypass?'AUTHENTICATED':'UNAUTHENTICATED');setPendingLogin(null);router.replace('/login');}
  },[bypass,router]);

  const hasPermission=useCallback((...permissions:string[])=>{const assigned=user?.permissions??[];return assigned.includes('*')||permissions.some(permission=>assigned.includes(permission));},[user]);
  const setAdministrationTenantId=useCallback((tenantId:string)=>{
    const normalized=tenantId.trim();
    if(!user?.roles.includes('INSTANCE_ROOT')){
      const fixed=user?.selectedTenantId??'';
      if(normalized&&normalized!==fixed)throw new Error('Tenant context is fixed by the authenticated home workspace and cannot be switched from this session.');
      setAdministrationTenantIdState(fixed);
      setCoreTenantContext(fixed);
      return;
    }
    setAdministrationTenantIdState(normalized);
    writeStoredRootAdministrationTenant(normalized);
    setCoreTenantContext(normalized);
  },[user]);

  useEffect(()=>{statusRef.current=status;},[status]);
  useEffect(()=>{
    const root=user?.roles.includes('INSTANCE_ROOT')===true;
    setCoreTenantContext(root?administrationTenantId:(user?.selectedTenantId??''));
    // Mirror an existing Root workspace selection into the server-readable hint cookie during
    // hydration as well as explicit switching. This keeps direct RSC URLs functional after an
    // upgrade when sessionStorage already contains the selected administration Tenant.
    if(root&&administrationTenantId)writeStoredRootAdministrationTenant(administrationTenantId);
  },[administrationTenantId,user?.roles,user?.selectedTenantId]);
  useEffect(()=>{
    if(status!=='AUTHENTICATED'||user?.roles.includes('INSTANCE_ROOT')!==true)return;
    let cancelled=false;
    void accessManagementApi.tenants(0,100,'','ACTIVE').then(page=>{
      if(cancelled)return;
      const options=(page.items??[]).map(tenant=>({
        tenantId:tenant.tenantId,
        tenantCode:tenant.tenantCode,
        tenantName:tenant.tenantName,
        membershipStatus:tenant.status??'ACTIVE',
        roleSummary:['INSTANCE_ROOT'],
        selected:tenant.tenantId===administrationTenantId,
      }));
      setTenants(options);
      if(!administrationTenantId&&options.length===1){
        const only=options[0].tenantId;
        setAdministrationTenantIdState(only);
        writeStoredRootAdministrationTenant(only);
        setCoreTenantContext(only);
      }
    }).catch(()=>{
      // A Tenant-directory read failure must not destroy a Root scope already selected by the operator.
    });
    return()=>{cancelled=true;};
  },[administrationTenantId,status,user?.roles]);
  useEffect(()=>{void refreshCurrentUser();},[refreshCurrentUser]);
  useEffect(()=>{
    const handler=()=>{
      const now=Date.now();
      if(unauthorizedValidationInFlight.current||now-lastUnauthorizedValidationAt.current<2_000)return;
      lastUnauthorizedValidationAt.current=now;
      unauthorizedValidationInFlight.current=true;
      void refreshCurrentUser().finally(()=>{unauthorizedValidationInFlight.current=false;});
    };
    window.addEventListener(UNAUTHORIZED_EVENT,handler);
    return()=>window.removeEventListener(UNAUTHORIZED_EVENT,handler);
  },[refreshCurrentUser]);
  useEffect(()=>{
    if(bypass)return;
    const publicRoute=PUBLIC_ROUTES.some(route=>pathname===route||pathname.startsWith(`${route}/`));
    if(status==='UNAUTHENTICATED'&&!publicRoute){router.replace('/login');return;}
    if(status==='AUTHENTICATED'&&user){const required=requiredRoute(user);if(required&&pathname!==required){router.replace(required);return;}if(!required&&(pathname==='/login'||pathname==='/change-password'||pathname==='/mfa-enrollment'))router.replace(authenticatedHome());}
  },[bypass,pathname,router,status,user]);

  const selectedTenantId=user?.selectedTenantId??'';
  const activeTenantId=user?.roles.includes('INSTANCE_ROOT')?administrationTenantId:selectedTenantId;
  const value=useMemo(()=>({status,user,tenants,selectedTenantId,activeTenantId,administrationTenantId,setAdministrationTenantId,pendingLogin,login,verifyMfa,changePassword,forgotPassword,resetPassword,activateInvitation,beginMfaEnrollment,confirmMfaEnrollment,logout,refreshCurrentUser,hasPermission}),[status,user,tenants,selectedTenantId,activeTenantId,administrationTenantId,setAdministrationTenantId,pendingLogin,login,verifyMfa,changePassword,forgotPassword,resetPassword,activateInvitation,beginMfaEnrollment,confirmMfaEnrollment,logout,refreshCurrentUser,hasPermission]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
