import { RouteState } from '@/components/common/RouteState';

export default function NotFound() {
  return (
    <RouteState
      title="Page not found"
      description="This admin page does not exist, or the selected event, task, or trace is no longer available."
      actionLabel="Back to Dashboard"
      actionHref="/dashboard"
    />
  );
}
