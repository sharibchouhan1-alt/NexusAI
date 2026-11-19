import Link from "next/link";
import { EmptyState } from "@/components/ui/states";

export default function NotFound() {
  return (
    <EmptyState
      title="Page not found"
      description="The page you're looking for doesn't exist."
      action={
        <Link href="/" className="text-sm font-medium text-indigo-600 hover:underline dark:text-indigo-400">
          Go to dashboard
        </Link>
      }
    />
  );
}
