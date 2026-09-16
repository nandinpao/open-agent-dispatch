'use client';
import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import { BusinessIssueConsole } from '@/components/issues-events/BusinessIssueConsole';
export default function BusinessIssuesPage(){return <EntitlementPageGuard featureId="business-issues"><BusinessIssueConsole /></EntitlementPageGuard>;}
